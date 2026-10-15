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
internal sealed record PairingView(PairingPhase Phase, string UserCode, string Url, string Message, DateTime At);

/// <summary>
/// Associe ce jeu à un compte du site, sans copier-coller (même principe que la connexion d'une TV) :
///   1. demande un code au serveur et l'affiche dans le jeu ;
///   2. ouvre la page du site, où l'utilisateur connecté saisit ce code et approuve ;
///   3. interroge le serveur jusqu'à recevoir le token d'API, puis le range dans ses réglages.
/// Le token n'est jamais affiché ni écrit dans les logs.
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

    public static volatile PairingView View = new(PairingPhase.Idle, "", "", "", DateTime.UtcNow);

    /// <summary>Démarre l'appairage s'il n'y a pas de token et qu'il n'est pas déjà en cours.</summary>
    public static void EnsureStarted()
    {
        lock (Gate)
        {
            if (_cts is { IsCancellationRequested: false })
                return;
            if (!DeathTrackerModule.Settings.Enabled || HasToken())
                return;

            _cts = new CancellationTokenSource();
            var ct = _cts.Token;
            _ = Task.Run(() => RunAsync(ct), ct);
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

    /// <summary>Oublie le token courant (révoqué, ou demande de l'utilisateur) et recommence.</summary>
    public static void Reset()
    {
        Stop();
        DeathTrackerModule.Settings.ApiToken = "";
        DeathTrackerModule.Instance.SaveSettings();
        EnsureStarted();
    }

    private static bool HasToken() => !string.IsNullOrWhiteSpace(DeathTrackerModule.Settings.ApiToken);

    private static async Task RunAsync(CancellationToken ct)
    {
        var browserOpened = false;
        try
        {
            while (!ct.IsCancellationRequested && !HasToken())
            {
                StartResponse? start;
                try
                {
                    start = await PostAsync<StartResponse>("/api/pairing/start",
                        new { deviceName = Environment.MachineName }, ct).ConfigureAwait(false);
                }
                catch (Exception ex) when (ex is not OperationCanceledException)
                {
                    Fail("Serveur injoignable, nouvelle tentative…", ex.Message);
                    await Task.Delay(TimeSpan.FromSeconds(15), ct).ConfigureAwait(false);
                    continue;
                }

                if (start is null || string.IsNullOrEmpty(start.DeviceCode) || string.IsNullOrEmpty(start.UserCode))
                {
                    Fail("Réponse inattendue du serveur.", "start: réponse vide");
                    await Task.Delay(TimeSpan.FromSeconds(30), ct).ConfigureAwait(false);
                    continue;
                }

                View = new PairingView(PairingPhase.Waiting, start.UserCode, start.VerificationUrl ?? "", "", DateTime.UtcNow);
                Logger.Log(LogLevel.Info, "DeathTracker", $"Appairage : saisis le code {start.UserCode} sur {start.VerificationUrl}");

                // Une seule ouverture du navigateur par lancement du jeu : pas de fenêtres en rafale.
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
                        poll = await PostAsync<PollResponse>("/api/pairing/poll",
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
                            DeathTrackerModule.Settings.ApiToken = poll.Token;
                            DeathTrackerModule.Instance.SaveSettings();
                            View = new PairingView(PairingPhase.Paired, "", "", "", DateTime.UtcNow);
                            Logger.Log(LogLevel.Info, "DeathTracker", "Jeu associé au compte.");
                            return;

                        case "denied":
                            Fail("Association refusée sur le site.", "refusée par l'utilisateur");
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
            // arrêt normal (déchargement du mod)
        }
        catch (Exception ex)
        {
            Fail("Erreur d'association.", ex.ToString());
        }
    }

    private static async Task<T?> PostAsync<T>(string path, object body, CancellationToken ct)
    {
        var url = DeathTrackerModule.BaseUrl + path;
        using var request = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(JsonSerializer.Serialize(body, Json), Encoding.UTF8, "application/json")
        };
        using var response = await Http.SendAsync(request, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        var text = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        return JsonSerializer.Deserialize<T>(text, Json);
    }

    private static void Fail(string message, string detail)
    {
        View = new PairingView(PairingPhase.Failed, "", "", message, DateTime.UtcNow);
        Logger.Log(LogLevel.Warn, "DeathTracker", $"Appairage : {message} ({detail})");
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

    public PairingOverlay(Game game) : base(game)
    {
        DrawOrder = int.MaxValue; // par-dessus tout
    }

    public override void Draw(GameTime gameTime)
    {
        var view = PairingManager.View;
        string? title = null, line = null, code = null;
        var color = Color.White;

        switch (view.Phase)
        {
            case PairingPhase.Waiting:
                title = "Death Tracker : associe ce jeu à ton compte";
                line = $"Sur {ShortUrl(view.Url)}, connecte-toi puis saisis ce code :";
                code = view.UserCode;
                break;
            case PairingPhase.Paired when DateTime.UtcNow - view.At < PairedBannerDuration:
                title = "Death Tracker : jeu associé à ton compte";
                color = Color.LightGreen;
                break;
            case PairingPhase.Failed:
                title = "Death Tracker : " + view.Message;
                color = Color.Orange;
                break;
        }

        if (title is null)
            return;

        var height = code is null ? 70f : 200f;
        const float width = 1180f;
        var x = (1920f - width) / 2f;
        const float y = 20f;

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
