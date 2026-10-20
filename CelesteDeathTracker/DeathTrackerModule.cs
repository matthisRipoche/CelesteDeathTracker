using Celeste;
using Celeste.Mod;

namespace CelesteDeathTracker;

/// <summary>
/// Point d'entrée chargé par Everest : cycle de vie du mod et menu. Le travail est délégué à
/// <see cref="GameHooks"/> (écoute du jeu), <see cref="ApiClient"/> (envoi) et
/// <see cref="PairingManager"/> (association au compte).
/// </summary>
public class DeathTrackerModule : EverestModule
{
    public static DeathTrackerModule Instance { get; private set; } = null!;

    public override Type SettingsType => typeof(DeathTrackerSettings);
    public static DeathTrackerSettings Settings => (DeathTrackerSettings)Instance._Settings;

    // Vrai une fois les réglages chargés et migrés : avant, on ne réagit pas aux changements
    // de serveur (le chargement du YAML appelle lui aussi les accesseurs).
    private static volatile bool _ready;

    private PairingOverlay? _overlay;

    public DeathTrackerModule()
    {
        Instance = this;
    }

    public override void Load()
    {
        GameHooks.Register();
        Logger.Log(LogLevel.Info, "DeathTracker", "Death Tracker chargé !");
    }

    // Appelé quand le moteur est prêt : on y ajoute le bandeau d'appairage et on lance
    // l'association automatique si le serveur choisi n'a pas encore de token.
    public override void LoadContent(bool firstLoad)
    {
        if (Settings.MigrateLegacy())
            SaveSettings();
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
        GameHooks.Unregister();

        _ready = false;
        PairingManager.Stop();
        if (_overlay is not null)
        {
            Monocle.Engine.Instance?.Components.Remove(_overlay);
            _overlay = null;
        }
    }

    /// <summary>Appelé par le réglage quand l'utilisateur change de serveur dans le menu.</summary>
    internal static void OnServerChanged()
    {
        if (!_ready) return;
        // La tentative en cours a été annoncée à l'ancien serveur seulement : la suivante repartira
        // proprement (avec son événement de départ) sur le nouveau.
        GameHooks.ForgetAttempt();
        PairingManager.Stop();
        PairingManager.ClearView();
        PairingManager.EnsureStarted();
    }

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
        var state = Servers.TokenFor(active).Length > 0 ? "jeu associé" : "association en attente (voir le bandeau)";
        menu.Add(new TextMenu.SubHeader($"{Servers.NameOf(active)} : {state}"));

        menu.Add(new TextMenu.Button("Associer ce jeu à un autre compte")
            .Pressed(() => PairingManager.Reset(Settings.Server)));
    }
}
