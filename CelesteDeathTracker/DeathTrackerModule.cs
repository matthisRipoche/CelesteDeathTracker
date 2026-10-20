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
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        // Les champs facultatifs absents (mapName, reason…) ne sont pas envoyés
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull
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
        // La tentative en cours a été annoncée à l'ancien serveur seulement : la suivante repartira
        // proprement (avec son événement de départ) sur le nouveau.
        _attempt = null;
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
        On.Celeste.Player.Die += OnPlayerDie;
        Everest.Events.Level.OnLoadLevel   += OnLoadLevel;
        Everest.Events.Level.OnTransitionTo += OnTransitionTo;
        Everest.Events.Level.OnExit        += OnLevelExit;
        Everest.Events.Celeste.OnExiting   += OnGameExiting;
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
        On.Celeste.Player.Die -= OnPlayerDie;
        Everest.Events.Level.OnLoadLevel   -= OnLoadLevel;
        Everest.Events.Level.OnTransitionTo -= OnTransitionTo;
        Everest.Events.Level.OnExit        -= OnLevelExit;
        Everest.Events.Celeste.OnExiting   -= OnGameExiting;

        _attempt = null;
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

        // registerStats = false : le jeu ne compte pas cette mort dans ses statistiques (mort
        // « technique »), on ne l'envoie pas non plus.
        if (!Settings.Enabled || !registerStats || result is null || self.Scene is not Level level)
            return result!;

        // La tentative est normalement déjà ouverte par OnLoadLevel ; sinon (mod activé en cours
        // de partie) on la démarre ici pour que la mort lui soit rattachée.
        var attempt = EnsureAttempt(level);

        _ = PostAsync("/api/ingest/deaths", new DeathEvent
        {
            Timestamp     = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            Chapter       = level.Session.Area.GetSID(),
            Room          = level.Session.Level,
            DeathX        = (int)self.Position.X,
            DeathY        = (int)self.Position.Y,
            TotalDeaths   = SaveData.Instance?.TotalDeaths ?? 0,
            SessionDeaths = level.Session.Deaths,
            TimeInRoomMs  = level.Session.Time / 10_000L,
            AttemptId     = attempt?.Id,
            Side          = attempt?.Side,
            SaveSlot      = attempt?.SaveSlot
        });

        return result;
    }

    // ------------------------------------------------------------------ tentatives

    /// <summary>
    /// Une tentative = une partie sur une face de map, de l'entrée à la sortie (terminée, recommencée,
    /// abandonnée ou quittée). Une mort NE la termine PAS : le jeu recharge le niveau mais la partie
    /// continue, c'est le serveur qui sait qu'un deathless est perdu à la première mort.
    /// </summary>
    /// <summary>
    /// La tentative en cours, avec les dernières valeurs vues de la session du jeu. Le redémarrage
    /// rapide (touche « recommencer » après une mort) ne déclenche PAS OnExit et réutilise le même
    /// objet Session en remettant son temps et ses morts à zéro : le seul signal fiable est donc qu'une
    /// de ces valeurs RECULE. Un respawn après une mort les laisse monter.
    /// </summary>
    private sealed class AttemptContext
    {
        public AttemptContext(string id, string chapter, string side, int? saveSlot, Session session)
        {
            Id = id; Chapter = chapter; Side = side; SaveSlot = saveSlot;
            Session = session;
            Touch(session);
        }

        public string Id { get; }
        public string Chapter { get; }
        public string Side { get; }
        public int? SaveSlot { get; }
        public Session Session { get; }
        public long LastTimeTicks { get; private set; }
        public int LastDeaths { get; private set; }
        public string LastRoom { get; private set; } = "";

        /// <summary>Mémorise l'état de la session pour pouvoir clore la tentative avec ses vraies valeurs.</summary>
        public void Touch(Session session)
        {
            LastTimeTicks = session.Time;
            LastDeaths = session.Deaths;
            LastRoom = session.Level ?? "";
        }

        public bool WentBackwards(Session session) =>
            !ReferenceEquals(session, Session) || session.Time < LastTimeTicks || session.Deaths < LastDeaths;
    }

    private static volatile AttemptContext? _attempt;

    private static string SideOf(AreaKey area) => area.Mode switch
    {
        AreaMode.BSide => "B",
        AreaMode.CSide => "C",
        _ => "A"
    };

    private static long Now() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    /// <summary>Nom officiel de la map tel qu'affiché dans le jeu (traduit), ou null s'il est introuvable.</summary>
    private static string? OfficialMapName(Session session)
    {
        try
        {
            var key = AreaData.Get(session)?.Name;
            if (string.IsNullOrEmpty(key)) return null;
            var name = Dialog.Clean(key);
            return string.IsNullOrWhiteSpace(name) ? null : name;
        }
        catch (Exception ex)
        {
            Logger.Log(LogLevel.Verbose, "DeathTracker", $"Nom de map introuvable : {ex.Message}");
            return null;
        }
    }

    /// <summary>
    /// Renvoie la tentative en cours pour ce niveau, en en ouvrant une si besoin (premier niveau chargé,
    /// ou changement de map/face sans passer par une sortie). Les événements portent tous la face et la
    /// sauvegarde : le serveur reconstruit la tentative même si l'un d'eux se perd.
    /// </summary>
    private static AttemptContext? EnsureAttempt(Level level)
    {
        if (!Settings.Enabled) return null;

        var session = level.Session;
        var chapter = session.Area.GetSID();
        var side = SideOf(session.Area);

        var current = _attempt;
        var sameMap = current is not null && current.Chapter == chapter && current.Side == side;
        if (current is not null && sameMap && !current.WentBackwards(session))
        {
            current.Touch(session);
            return current;
        }

        // Session remise à zéro sur la même face = chapitre recommencé ; autre map ou face sans sortie
        // intermédiaire = "switched". On clôt l'ancienne avec SES dernières valeurs (room et temps de la
        // mort, par exemple), pas celles de la session déjà réinitialisée.
        if (current is not null)
            SendAttemptEvent("attempt_end", current, current.LastRoom, current.LastTimeTicks,
                reason: sameMap ? "restart" : "switched");

        var attempt = new AttemptContext(Guid.NewGuid().ToString(), chapter, side, SaveData.Instance?.FileSlot, session);
        _attempt = attempt;

        SendAttemptEvent("attempt_start", attempt, session.Level, session.Time,
            mapName: OfficialMapName(session), startedFromBeginning: session.StartedFromBeginning);
        return attempt;
    }

    private static void SendAttemptEvent(string type, AttemptContext attempt, string room, long sessionTicks,
        string? mapName = null, bool? startedFromBeginning = null, string? reason = null, bool wait = false)
    {
        if (!Settings.Enabled) return;
        var sent = PostAsync("/api/ingest/events", new AttemptEvent
        {
            Type                 = type,
            AttemptId            = attempt.Id,
            Chapter              = attempt.Chapter,
            Side                 = attempt.Side,
            SaveSlot             = attempt.SaveSlot,
            Room                 = room,
            Timestamp            = Now(),
            SessionTimeMs        = sessionTicks / 10_000L,
            MapName              = mapName,
            StartedFromBeginning = startedFromBeginning,
            Reason               = reason
        });

        // À la fermeture du jeu le processus s'arrête aussitôt : on attend (brièvement) que l'envoi parte.
        if (wait)
        {
            try { sent.Wait(1500); } catch { /* le jeu se ferme : rien à faire d'une erreur d'envoi */ }
        }
    }

    // Fermeture du jeu : sans cela, la tentative resterait « ouverte » côté serveur et le live afficherait
    // « En jeu » pendant des heures. La sortie normale d'une map passe déjà par OnLevelExit.
    private static void OnGameExiting()
    {
        var attempt = _attempt;
        _attempt = null;
        if (attempt is null) return;
        SendAttemptEvent("attempt_end", attempt, attempt.LastRoom, attempt.LastTimeTicks,
            reason: "game_closed", wait: true);
    }

    // Appelé à chaque chargement de niveau (entrée dans la map, respawn après une mort, redémarrage) :
    // on s'en sert seulement pour ouvrir la tentative, le respawn ne crée rien de plus.
    private static void OnLoadLevel(Level level, Player.IntroTypes playerIntro, bool isFromLoader)
    {
        EnsureAttempt(level);
    }

    // Vraie transition d'écran vers une nouvelle room (pas un respawn dans la même room).
    private static void OnTransitionTo(Level level, LevelData next, Vector2 direction)
    {
        var attempt = EnsureAttempt(level);
        if (attempt is not null)
            SendAttemptEvent("room_enter", attempt, next.Name, level.Session.Time);
    }

    private static void OnLevelExit(Level level, LevelExit exit, LevelExit.Mode mode, Session session, HiresSnow snow)
    {
        var attempt = _attempt;
        _attempt = null;
        if (attempt is null) return;

        var reason = mode switch
        {
            LevelExit.Mode.Completed or LevelExit.Mode.CompletedInterlude => "completed",
            LevelExit.Mode.Restart => "restart",
            LevelExit.Mode.GoldenBerryRestart => "golden_restart",
            LevelExit.Mode.SaveAndQuit => "save_and_quit",
            _ => "give_up"
        };
        SendAttemptEvent("attempt_end", attempt, session.Level, session.Time, reason: reason);
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
    public long   TimeInRoomMs  { get; init; }   // temps de la PARTIE (Session.Time), pas de la room

    // Contexte de la tentative (null si le mod a été activé en cours de partie)
    public string? AttemptId    { get; init; }
    public string? Side         { get; init; }   // "A", "B" ou "C"
    public int?    SaveSlot     { get; init; }
}

/// <summary>attempt_start, room_enter ou attempt_end (cf. docs/data-model.md).</summary>
public record AttemptEvent
{
    public string  Type                 { get; init; } = "";
    public string  AttemptId            { get; init; } = "";
    public string  Chapter              { get; init; } = "";
    public string  Side                 { get; init; } = "A";
    public int?    SaveSlot             { get; init; }
    public string  Room                 { get; init; } = "";
    public long    Timestamp            { get; init; }
    public long    SessionTimeMs        { get; init; }
    public string? MapName              { get; init; }
    public bool?   StartedFromBeginning { get; init; }
    public string? Reason               { get; init; }
}
