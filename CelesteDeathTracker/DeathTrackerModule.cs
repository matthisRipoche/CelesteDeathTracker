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

/// <summary>Serveur vers lequel les morts sont envoyées. Chaque serveur a son propre token.</summary>
public enum ServerTarget { Production, Local }

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

    // Vrai une fois les réglages chargés et migrés : avant, on ne réagit pas aux changements
    // de serveur (le chargement du YAML appelle lui aussi les accesseurs).
    private static volatile bool _ready;

    // ------------------------------------------------------------------ serveur actif

    internal static string NameOf(ServerTarget target) => target == ServerTarget.Production ? "Production" : "Local";

    internal static string UrlFor(ServerTarget target) =>
        (target == ServerTarget.Production ? Settings.ProductionUrl : Settings.LocalUrl).Trim().TrimEnd('/');

    internal static string TokenFor(ServerTarget target) =>
        (target == ServerTarget.Production ? Settings.ProductionToken : Settings.LocalToken).Trim();

    internal static void SetToken(ServerTarget target, string token)
    {
        if (target == ServerTarget.Production) Settings.ProductionToken = token;
        else Settings.LocalToken = token;
    }

    /// <summary>Appelé par le réglage quand l'utilisateur change de serveur dans le menu.</summary>
    internal static void OnServerChanged()
    {
        if (!_ready) return;
        PairingManager.Stop();
        PairingManager.ClearView();
        PairingManager.EnsureStarted();
    }

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
    // l'association automatique si le serveur choisi n'a pas encore de token.
    public override void LoadContent(bool firstLoad)
    {
        MigrateLegacySettings();
        _ready = true;

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

        _ready = false;
        PairingManager.Stop();
        if (_overlay is not null)
        {
            Monocle.Engine.Instance?.Components.Remove(_overlay);
            _overlay = null;
        }
    }

    /// <summary>
    /// Anciennes versions : une seule adresse (ServerUrl) et un seul token (ApiToken). On range le
    /// token sous le bon serveur pour que personne n'ait à se ré-associer, puis on vide l'ancien champ.
    /// </summary>
    private void MigrateLegacySettings()
    {
        var s = Settings;
        var legacyToken = (s.ApiToken ?? "").Trim();
        var legacyUrl = (s.ServerUrl ?? "").Trim();
        if (legacyToken.Length == 0 && legacyUrl.Length == 0)
            return;

        if (legacyToken.Length > 0)
        {
            var isLocal = legacyUrl.Length == 0 || IsLocalUrl(legacyUrl);
            if (isLocal && s.LocalToken.Trim().Length == 0) s.LocalToken = legacyToken;
            if (!isLocal && s.ProductionToken.Trim().Length == 0) s.ProductionToken = legacyToken;
            Logger.Log(LogLevel.Info, "DeathTracker",
                $"Réglages migrés : ancien token rangé sous « {(isLocal ? "Local" : "Production")} ».");
        }

        s.ApiToken = "";
        s.ServerUrl = "";
        SaveSettings();
    }

    private static bool IsLocalUrl(string url) =>
        Uri.TryCreate(url, UriKind.Absolute, out var u) &&
        (u.IsLoopback || u.Host.Equals("localhost", StringComparison.OrdinalIgnoreCase));

    // ------------------------------------------------------------------ menu

    // Menu du mod, entièrement en français : activation, choix du serveur, état, ré-association.
    public override void CreateModMenuSection(TextMenu menu, bool inGame, FMOD.Studio.EventInstance snapshot)
    {
        CreateModMenuSectionHeader(menu, inGame, snapshot);

        menu.Add(new TextMenu.OnOff("Envoyer mes morts au serveur", Settings.Enabled).Change(enabled =>
        {
            Settings.Enabled = enabled;
            if (enabled) PairingManager.EnsureStarted();
            else PairingManager.Stop();
        }));

        var server = new TextMenu.Option<ServerTarget>("Serveur");
        server.Add("Production", ServerTarget.Production, Settings.Server == ServerTarget.Production);
        server.Add("Local (développement)", ServerTarget.Local, Settings.Server == ServerTarget.Local);
        server.Change(target =>
        {
            Settings.Server = target;   // le réglage relance l'association si ce serveur n'a pas de token
            SaveSettings();
        });
        menu.Add(server);

        var active = Settings.Server;
        var state = TokenFor(active).Length > 0 ? "jeu associé" : "association en attente (voir le bandeau)";
        menu.Add(new TextMenu.SubHeader($"{NameOf(active)} : {state}"));

        menu.Add(new TextMenu.Button("Associer ce jeu à un autre compte")
            .Pressed(() => PairingManager.Reset(Settings.Server)));
    }

    // ------------------------------------------------------------------ envoi des événements

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

        _ = PostAsync("/api/ingest/deaths", new DeathEvent
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
        _ = PostAsync("/api/ingest/events", new SessionEvent
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
        _ = PostAsync("/api/ingest/events", new SessionEvent
        {
            Type      = "session_end",
            Chapter   = self.Session.Area.GetSID(),
            Room      = self.Session.Level,
            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        });
    }

    private static async Task PostAsync<T>(string path, T data)
    {
        // On fige le serveur au moment de l'envoi : si le joueur en change pendant la requête,
        // la réponse (ex. 401) doit être attribuée à celui qui l'a émise.
        var target = Settings.Server;
        var token = TokenFor(target);

        // Sans token le serveur répondrait 401 : inutile d'envoyer quoi que ce soit.
        if (token.Length == 0)
            return;

        var url = UrlFor(target) + path;
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
                Logger.Log(LogLevel.Warn, "DeathTracker",
                    $"Token refusé par le serveur {NameOf(target)} : nouvelle association.");
                PairingManager.Reset(target);
            }
            else if (!response.IsSuccessStatusCode)
            {
                Logger.Log(LogLevel.Warn, "DeathTracker",
                    $"Le serveur {NameOf(target)} a refusé l'envoi ({(int)response.StatusCode}).");
            }
        }
        catch (Exception ex)
        {
            Logger.Log(LogLevel.Verbose, "DeathTracker", $"Envoi échoué vers {url} : {ex.Message}");
        }
    }
}

/// <summary>
/// Réglages enregistrés dans Saves/modsettings-CelesteDeathTracker.celeste.
/// Tout est masqué de l'affichage automatique (SettingIgnore) : le menu est construit à la main
/// dans CreateModMenuSection, avec des libellés lisibles.
/// </summary>
public class DeathTrackerSettings : EverestModuleSettings
{
    [SettingIgnore]
    public bool Enabled { get; set; } = true;

    private ServerTarget _server = ServerTarget.Production;

    /// <summary>Serveur actif. Production par défaut ; « Local » sert au développement.</summary>
    [SettingIgnore]
    public ServerTarget Server
    {
        get => _server;
        set
        {
            if (_server == value) return;
            _server = value;
            DeathTrackerModule.OnServerChanged();
        }
    }

    // Adresses sans "/api". Modifiables dans le fichier YAML si l'adresse change.
    [SettingIgnore]
    public string ProductionUrl { get; set; } = "https://celeste.matthisripoche.com";

    [SettingIgnore]
    public string LocalUrl { get; set; } = "http://localhost:5000";

    // Remplis automatiquement par l'association (voir Pairing.cs). Un token par serveur : passer
    // de l'un à l'autre ne demande donc pas de se ré-associer.
    [SettingIgnore]
    public string ProductionToken { get; set; } = "";

    [SettingIgnore]
    public string LocalToken { get; set; } = "";

    // Anciennes versions du mod (une seule adresse, un seul token). Lus pour la migration, puis vidés.
    [SettingIgnore]
    public string ServerUrl { get; set; } = "";

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
