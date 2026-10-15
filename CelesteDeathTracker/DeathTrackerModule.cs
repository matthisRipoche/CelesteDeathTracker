using Celeste;
using Celeste.Mod;
using Microsoft.Xna.Framework;
using System;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace CelesteDeathTracker;

public class DeathTrackerModule : EverestModule
{
    public static DeathTrackerModule Instance { get; private set; } = null!;

    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(2) };

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public override Type SettingsType => typeof(DeathTrackerSettings);
    public static DeathTrackerSettings Settings => (DeathTrackerSettings)Instance._Settings;

    // Le serveur déduit le compte du token : on n'envoie jamais d'identifiant utilisateur.
    internal static string BaseUrl  => Settings.ServerUrl.Trim().TrimEnd('/');
    private static string DeathsUrl => $"{BaseUrl}/api/ingest/deaths";
    private static string EventsUrl => $"{BaseUrl}/api/ingest/events";

    public DeathTrackerModule()
    {
        Instance = this;
    }

    public override void Load()
    {
        On.Celeste.Player.Die  += OnPlayerDie;
        On.Celeste.Level.Begin += OnLevelBegin;
        On.Celeste.Level.End   += OnLevelEnd;
        Logger.Log(LogLevel.Info, "DeathTracker", "Death Tracker chargé !");
    }

    private PairingOverlay? _overlay;

    // Appelé quand le moteur est prêt : on y ajoute le bandeau d'appairage et on lance
    // l'association automatique si aucun token n'est encore enregistré.
    public override void LoadContent(bool firstLoad)
    {
        if (_overlay is null && Monocle.Engine.Instance is { } engine)
        {
            _overlay = new PairingOverlay(engine);
            engine.Components.Add(_overlay);
        }
        PairingManager.EnsureStarted();
    }

    public override void Unload()
    {
        On.Celeste.Player.Die  -= OnPlayerDie;
        On.Celeste.Level.Begin -= OnLevelBegin;
        On.Celeste.Level.End   -= OnLevelEnd;

        PairingManager.Stop();
        if (_overlay is not null)
        {
            Monocle.Engine.Instance?.Components.Remove(_overlay);
            _overlay = null;
        }
    }

    // Menu du mod : le réglage normal + un bouton pour re-associer le jeu à un autre compte.
    public override void CreateModMenuSection(TextMenu menu, bool inGame, FMOD.Studio.EventInstance snapshot)
    {
        CreateModMenuSectionHeader(menu, inGame, snapshot);
        menu.Add(new TextMenu.Button("Associer ce jeu à un autre compte").Pressed(PairingManager.Reset));
        base.CreateModMenuSection(menu, inGame, snapshot);
    }

    private static PlayerDeadBody OnPlayerDie(
        On.Celeste.Player.orig_Die orig,
        Player self,
        Vector2 direction,
        bool evenIfInvincible,
        bool registerStats)
    {
        var result = orig(self, direction, evenIfInvincible, registerStats);

        if (!Settings.Enabled || result is null || self.Scene is not Level level)
            return result!;

        _ = PostAsync(DeathsUrl, new DeathEvent
        {
            Timestamp     = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            Chapter       = level.Session.Area.GetSID(),
            Room          = level.Session.Level,
            DeathX        = (int)self.Position.X,
            DeathY        = (int)self.Position.Y,
            TotalDeaths   = SaveData.Instance?.TotalDeaths ?? 0,
            SessionDeaths = level.Session.Deaths,
            TimeInRoomMs  = level.Session.Time / 10_000L
        });

        return result;
    }

    private static void OnLevelBegin(On.Celeste.Level.orig_Begin orig, Level self)
    {
        orig(self);
        if (!Settings.Enabled) return;
        _ = PostAsync(EventsUrl, new SessionEvent
        {
            Type      = "session_start",
            Chapter   = self.Session.Area.GetSID(),
            Room      = self.Session.Level,
            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        });
    }

    private static void OnLevelEnd(On.Celeste.Level.orig_End orig, Level self)
    {
        orig(self);
        if (!Settings.Enabled) return;
        _ = PostAsync(EventsUrl, new SessionEvent
        {
            Type      = "session_end",
            Chapter   = self.Session.Area.GetSID(),
            Room      = self.Session.Level,
            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        });
    }

    private static async Task PostAsync<T>(string url, T data)
    {
        // Sans token le serveur répondrait 401 : inutile d'envoyer quoi que ce soit.
        var token = Settings.ApiToken.Trim();
        if (token.Length == 0)
            return;

        try
        {
            var json = JsonSerializer.Serialize(data, JsonOptions);
            using var request = new HttpRequestMessage(HttpMethod.Post, url)
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json")
            };
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

            using var response = await Http.SendAsync(request).ConfigureAwait(false);
            if (response.StatusCode == System.Net.HttpStatusCode.Unauthorized)
            {
                // Token révoqué ou supprimé côté site : on l'oublie et on relance l'association.
                Logger.Log(LogLevel.Warn, "DeathTracker", "Token refusé par le serveur : nouvelle association.");
                PairingManager.Reset();
            }
            else if (!response.IsSuccessStatusCode)
            {
                Logger.Log(LogLevel.Warn, "DeathTracker",
                    $"Le serveur a refusé l'envoi ({(int)response.StatusCode}).");
            }
        }
        catch (Exception ex)
        {
            Logger.Log(LogLevel.Verbose, "DeathTracker", $"Envoi échoué vers {url} : {ex.Message}");
        }
    }
}

public class DeathTrackerSettings : EverestModuleSettings
{
    [SettingName("DEATH_TRACKER_ENABLED")]
    public bool Enabled { get; set; } = true;

    // Adresse du serveur, sans "/api". En production : https://<ton-domaine>
    [SettingName("DEATH_TRACKER_SERVER_URL")]
    [SettingMaxLength(200)]
    public string ServerUrl { get; set; } = "http://localhost:5000";

    // Rempli automatiquement par l'association (voir Pairing.cs). Sans token, rien n'est envoyé.
    // Masqué du menu : plus personne n'a à le saisir. Reste modifiable dans le fichier YAML des réglages.
    [SettingIgnore]
    public string ApiToken { get; set; } = "";
}

public record DeathEvent
{
    public long   Timestamp     { get; init; }
    public string Chapter       { get; init; } = "";
    public string Room          { get; init; } = "";
    public int    DeathX        { get; init; }
    public int    DeathY        { get; init; }
    public int    TotalDeaths   { get; init; }
    public int    SessionDeaths { get; init; }
    public long   TimeInRoomMs  { get; init; }
}

public record SessionEvent
{
    public string Type      { get; init; } = "";
    public string Chapter   { get; init; } = "";
    public string Room      { get; init; } = "";
    public long   Timestamp { get; init; }
}
