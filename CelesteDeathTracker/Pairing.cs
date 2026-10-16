using Celeste;
using Celeste.Mod;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Monocle;
using System;
using System.Diagnostics;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace CelesteDeathTracker;

internal enum PairingPhase { Idle, Waiting, Paired, Failed }

/// <summary>Photo immuable de l'état d'appairage, lue par l'affichage sans verrou.</summary>
internal sealed record PairingView(
    PairingPhase Phase, string Server, string UserCode, string Url, string Message, DateTime At);

/// <summary>
/// Associe ce jeu à un compte du site, sans copier-coller (même principe que la connexion d'une TV) :
///   1. demande un code au serveur choisi et l'affiche dans le jeu ;
///   2. ouvre la page du site, où l'utilisateur connecté saisit ce code et approuve ;
///   3. interroge le serveur jusqu'à recevoir le token d'API, puis le range dans ses réglages.
/// Chaque serveur (Production / Local) a son propre token. Le token n'est jamais affiché ni journalisé.
/// </summary>
internal static class PairingManager
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(5) };

    private static readonly JsonSerializerOptions Json = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true
    };

    private static readonly object Gate = new();
    private static CancellationTokenSource? _cts;

    private static readonly PairingView IdleView = new(PairingPhase.Idle, "", "", "", "", DateTime.UtcNow);
    public static volatile PairingView View = IdleView;

    /// <summary>Démarre l'appairage du serveur actif s'il n'a pas de token et qu'il n'est pas en cours.</summary>
    public static void EnsureStarted()
    {
        lock (Gate)
        {
            if (_cts is { IsCancellationRequested: false })
                return;

            var target = DeathTrackerModule.Settings.Server;
            if (!DeathTrackerModule.Settings.Enabled || DeathTrackerModule.TokenFor(target).Length > 0)
                return;

            _cts = new CancellationTokenSource();
            var ct = _cts.Token;
            _ = Task.Run(() => RunAsync(target, ct), ct);
        }
    }

    public static void Stop()
    {
        lock (Gate)
        {
            _cts?.Cancel();
            _cts = null;
        }
    }

    public static void ClearView() => View = new PairingView(PairingPhase.Idle, "", "", "", "", DateTime.UtcNow);

    /// <summary>
    /// Oublie le token d'un serveur (révoqué, ou demande de l'utilisateur). Si c'est le serveur
    /// actif, une nouvelle association démarre aussitôt.
    /// </summary>
    public static void Reset(ServerTarget target)
    {
        DeathTrackerModule.SetToken(target, "");
        DeathTrackerModule.Instance.SaveSettings();

        if (DeathTrackerModule.Settings.Server == target)
        {
            Stop();
            ClearView();
            EnsureStarted();
        }
    }

    private static async Task RunAsync(ServerTarget target, CancellationToken ct)
    {
        var name = DeathTrackerModule.NameOf(target);
        var browserOpened = false;
        try
        {
            while (!ct.IsCancellationRequested && DeathTrackerModule.TokenFor(target).Length == 0)
            {
                StartResponse? start;
                try
                {
                    start = await PostAsync<StartResponse>(target, "/api/pairing/start",
                        new { deviceName = Environment.MachineName }, ct).ConfigureAwait(false);
                }
                catch (Exception ex) when (ex is not OperationCanceledException)
                {
                    Fail(name, "Serveur injoignable, nouvelle tentative…", ex.Message);
                    await Task.Delay(TimeSpan.FromSeconds(15), ct).ConfigureAwait(false);
                    continue;
                }

                if (start is null || string.IsNullOrEmpty(start.DeviceCode) || string.IsNullOrEmpty(start.UserCode))
                {
                    Fail(name, "Réponse inattendue du serveur.", "start : réponse vide");
                    await Task.Delay(TimeSpan.FromSeconds(30), ct).ConfigureAwait(false);
                    continue;
                }

                View = new PairingView(PairingPhase.Waiting, name, start.UserCode, start.VerificationUrl ?? "", "", DateTime.UtcNow);
                Logger.Log(LogLevel.Info, "DeathTracker",
                    $"Appairage ({name}) : saisis le code {start.UserCode} sur {start.VerificationUrl}");

                // Une seule ouverture du navigateur par tentative : pas de fenêtres en rafale.
                if (!browserOpened)
                    browserOpened = TryOpenBrowser(start.VerificationUrl);

                var deadline = DateTime.UtcNow.AddSeconds(Math.Max(30, start.ExpiresInSeconds));
                var interval = TimeSpan.FromSeconds(Math.Max(2, start.IntervalSeconds));
                var restart = false;

                while (!restart && DateTime.UtcNow < deadline)
                {
                    await Task.Delay(interval, ct).ConfigureAwait(false);

                    PollResponse? poll;
                    try
                    {
                        poll = await PostAsync<PollResponse>(target, "/api/pairing/poll",
                            new { deviceCode = start.DeviceCode }, ct).ConfigureAwait(false);
                    }
                    catch (Exception ex) when (ex is not OperationCanceledException)
                    {
                        Logger.Log(LogLevel.Verbose, "DeathTracker", $"Interrogation échouée : {ex.Message}");
                        continue; // coupure réseau passagère : on réessaie au prochain tour
                    }

                    switch (poll?.Status)
                    {
                        case "approved" when !string.IsNullOrEmpty(poll.Token):
                            // Le token est rangé sous le serveur qui l'a émis, même si le joueur
                            // a changé de serveur entre-temps.
                            DeathTrackerModule.SetToken(target, poll.Token);
                            DeathTrackerModule.Instance.SaveSettings();
                            View = new PairingView(PairingPhase.Paired, name, "", "", "", DateTime.UtcNow);
                            Logger.Log(LogLevel.Info, "DeathTracker", $"Jeu associé au compte ({name}).");
                            return;

                        case "denied":
                            Fail(name, "Association refusée sur le site.", "refusée par l'utilisateur");
                            await Task.Delay(TimeSpan.FromSeconds(60), ct).ConfigureAwait(false);
                            restart = true;
                            break;

                        case "expired":
                            restart = true; // le code a expiré : on en redemande un
                            break;
                    }
                }
            }
        }
        catch (OperationCanceledException)
        {
            // arrêt normal (changement de serveur, déchargement du mod)
        }
        catch (Exception ex)
        {
            Fail(name, "Erreur d'association.", ex.ToString());
        }
    }

    private static async Task<T?> PostAsync<T>(ServerTarget target, string path, object body, CancellationToken ct)
    {
        var url = DeathTrackerModule.UrlFor(target) + path;
        using var request = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(JsonSerializer.Serialize(body, Json), Encoding.UTF8, "application/json")
        };
        using var response = await Http.SendAsync(request, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        var text = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        return JsonSerializer.Deserialize<T>(text, Json);
    }

    private static void Fail(string server, string message, string detail)
    {
        var previous = View;
        // Même échec qu'au tour précédent : on garde l'heure d'origine, sinon le bandeau
        // clignoterait à chaque nouvelle tentative (serveur local éteint, par exemple).
        var at = previous.Phase == PairingPhase.Failed && previous.Message == message && previous.Server == server
            ? previous.At
            : DateTime.UtcNow;
        View = new PairingView(PairingPhase.Failed, server, "", "", message, at);
        Logger.Log(LogLevel.Warn, "DeathTracker", $"Appairage ({server}) : {message} ({detail})");
    }

    private static bool TryOpenBrowser(string? url)
    {
        // Uniquement http(s) : on n'exécute jamais autre chose qu'une page web.
        if (string.IsNullOrEmpty(url) ||
            !(url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
              url.StartsWith("https://", StringComparison.OrdinalIgnoreCase)))
            return false;
        try
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            return true;
        }
        catch (Exception ex)
        {
            Logger.Log(LogLevel.Warn, "DeathTracker", $"Impossible d'ouvrir le navigateur : {ex.Message}");
            return false;
        }
    }

    private sealed record StartResponse(string? DeviceCode, string? UserCode, string? VerificationUrl,
        int ExpiresInSeconds, int IntervalSeconds);

    private sealed record PollResponse(string? Status, string? Token);
}

/// <summary>
/// Bandeau affiché par-dessus le jeu (menu comme niveau) tant que l'appairage n'est pas terminé.
/// C'est ce qui dispense de chercher une option dans les menus.
/// </summary>
internal sealed class PairingOverlay : DrawableGameComponent
{
    private static readonly TimeSpan PairedBannerDuration = TimeSpan.FromSeconds(6);
    private static readonly TimeSpan FailedBannerDuration = TimeSpan.FromSeconds(8);

    public PairingOverlay(Game game) : base(game)
    {
        DrawOrder = int.MaxValue; // par-dessus tout
    }

    public override void Draw(GameTime gameTime)
    {
        var view = PairingManager.View;
        string? title = null, line = null, code = null;
        var color = Color.White;
        var age = DateTime.UtcNow - view.At;

        switch (view.Phase)
        {
            case PairingPhase.Waiting:
                title = $"Death Tracker ({view.Server}) : associe ce jeu à ton compte";
                line = $"Sur {ShortUrl(view.Url)}, connecte-toi puis saisis ce code :";
                code = view.UserCode;
                break;
            case PairingPhase.Paired when age < PairedBannerDuration:
                title = $"Death Tracker ({view.Server}) : jeu associé à ton compte";
                color = Color.LightGreen;
                break;
            case PairingPhase.Failed when age < FailedBannerDuration:
                title = $"Death Tracker ({view.Server}) : {view.Message}";
                color = Color.Orange;
                break;
        }

        if (title is null)
            return;

        var height = code is null ? 70f : 200f;
        const float width = 1180f;
        var x = (1920f - width) / 2f;
        const float y = 20f;

        // "Monocle.Draw" : dans cette classe, "Draw" seul désignerait la méthode ci-dessus.
        Monocle.Draw.SpriteBatch.Begin(SpriteSortMode.Deferred, BlendState.AlphaBlend, SamplerState.LinearClamp,
            DepthStencilState.None, RasterizerState.CullNone, null, Engine.ScreenMatrix);

        Monocle.Draw.Rect(x, y, width, height, Color.Black * 0.8f);
        ActiveFont.DrawOutline(title, new Vector2(1920f / 2f, y + 18f), new Vector2(0.5f, 0f),
            Vector2.One * 0.6f, color, 2f, Color.Black);

        if (line is not null)
            ActiveFont.DrawOutline(line, new Vector2(1920f / 2f, y + 70f), new Vector2(0.5f, 0f),
                Vector2.One * 0.5f, Color.White, 2f, Color.Black);

        if (code is not null)
            ActiveFont.DrawOutline(code, new Vector2(1920f / 2f, y + 112f), new Vector2(0.5f, 0f),
                Vector2.One * 1.1f, Color.Gold, 3f, Color.Black);

        Monocle.Draw.SpriteBatch.End();
    }

    private static string ShortUrl(string url) =>
        url.Replace("https://", "", StringComparison.OrdinalIgnoreCase)
           .Replace("http://", "", StringComparison.OrdinalIgnoreCase);
}
