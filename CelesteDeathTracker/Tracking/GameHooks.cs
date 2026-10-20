using Celeste;
using Celeste.Mod;
using Microsoft.Xna.Framework;

namespace CelesteDeathTracker;

/// <summary>
/// Écoute le jeu (hook MonoMod sur la mort, événements Everest des niveaux) et traduit ce qu'il voit
/// en événements pour le serveur. Les décisions sur les tentatives sont prises par
/// <see cref="AttemptTracker"/>, ici on ne fait que lire le jeu et envoyer.
/// </summary>
internal static class GameHooks
{
    private static readonly AttemptTracker Attempts = new();

    private static DeathTrackerSettings Settings => DeathTrackerModule.Settings;

    public static void Register()
    {
        On.Celeste.Player.Die += OnPlayerDie;
        Everest.Events.Level.OnLoadLevel    += OnLoadLevel;
        Everest.Events.Level.OnTransitionTo += OnTransitionTo;
        Everest.Events.Level.OnExit         += OnLevelExit;
        Everest.Events.Celeste.OnExiting    += OnGameExiting;
    }

    // Tout hook ajouté dans Register DOIT être retiré ici.
    public static void Unregister()
    {
        On.Celeste.Player.Die -= OnPlayerDie;
        Everest.Events.Level.OnLoadLevel    -= OnLoadLevel;
        Everest.Events.Level.OnTransitionTo -= OnTransitionTo;
        Everest.Events.Level.OnExit         -= OnLevelExit;
        Everest.Events.Celeste.OnExiting    -= OnGameExiting;
        ForgetAttempt();
    }

    /// <summary>
    /// Oublie la tentative en cours sans la clore côté serveur (changement de serveur : elle n'a été
    /// annoncée qu'à l'ancien, la suivante repartira proprement sur le nouveau).
    /// </summary>
    public static void ForgetAttempt() => Attempts.Close();

    // ------------------------------------------------------------------ mort

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

        _ = ApiClient.PostAsync("/api/ingest/deaths", new DeathEvent
        {
            Timestamp     = Now(),
            Chapter       = level.Session.Area.GetSID(),
            Room          = level.Session.Level,
            DeathX        = (int)self.Position.X,
            DeathY        = (int)self.Position.Y,
            TotalDeaths   = SaveData.Instance?.TotalDeaths ?? 0,
            SessionDeaths = level.Session.Deaths,
            TimeInRoomMs  = PayloadJson.TicksToMs(level.Session.Time),
            AttemptId     = attempt?.Id,
            Side          = attempt?.Side,
            SaveSlot      = attempt?.SaveSlot
        });

        return result;
    }

    // ------------------------------------------------------------------ tentatives

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
        var attempt = Attempts.Close();
        if (attempt is null) return;

        var reason = mode switch
        {
            LevelExit.Mode.Completed or LevelExit.Mode.CompletedInterlude => EndReasons.Completed,
            LevelExit.Mode.Restart => EndReasons.Restart,
            LevelExit.Mode.GoldenBerryRestart => EndReasons.GoldenRestart,
            LevelExit.Mode.SaveAndQuit => EndReasons.SaveAndQuit,
            _ => EndReasons.GiveUp
        };
        SendAttemptEvent("attempt_end", attempt, session.Level, session.Time, reason: reason);
    }

    // Fermeture du jeu : sans cela, la tentative resterait « ouverte » côté serveur et le live afficherait
    // « En jeu » pendant des heures. La sortie normale d'une map passe déjà par OnLevelExit.
    private static void OnGameExiting()
    {
        var attempt = Attempts.Close();
        if (attempt is null) return;
        SendAttemptEvent("attempt_end", attempt, attempt.LastRoom, attempt.LastTimeTicks,
            reason: EndReasons.GameClosed, wait: true);
    }

    /// <summary>Renvoie la tentative en cours pour ce niveau, en en ouvrant une si besoin.</summary>
    private static Attempt? EnsureAttempt(Level level)
    {
        if (!Settings.Enabled) return null;

        var session = level.Session;
        var change = Attempts.Observe(new GameSnapshot(
            Session:   session,
            Chapter:   session.Area.GetSID(),
            Side:      SideOf(session.Area),
            Room:      session.Level ?? "",
            TimeTicks: session.Time,
            Deaths:    session.Deaths,
            SaveSlot:  SaveData.Instance?.FileSlot));

        if (change.Ended is { } ended)
            SendAttemptEvent("attempt_end", ended, ended.LastRoom, ended.LastTimeTicks, reason: change.EndReason);

        if (change.Started)
            SendAttemptEvent("attempt_start", change.Current, session.Level ?? "", session.Time,
                mapName: OfficialMapName(session), startedFromBeginning: session.StartedFromBeginning);

        return change.Current;
    }

    private static void SendAttemptEvent(string type, Attempt attempt, string room, long sessionTicks,
        string? mapName = null, bool? startedFromBeginning = null, string? reason = null, bool wait = false)
    {
        if (!Settings.Enabled) return;
        var sent = ApiClient.PostAsync("/api/ingest/events",
            AttemptEvent.For(type, attempt, room, sessionTicks, Now(), mapName, startedFromBeginning, reason));

        // À la fermeture du jeu le processus s'arrête aussitôt : on attend (brièvement) que l'envoi parte.
        if (wait)
        {
            try { sent.Wait(1500); } catch { /* le jeu se ferme : rien à faire d'une erreur d'envoi */ }
        }
    }

    // ------------------------------------------------------------------ lecture du jeu

    private static long Now() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    private static string SideOf(AreaKey area) => area.Mode switch
    {
        AreaMode.BSide => "B",
        AreaMode.CSide => "C",
        _ => "A"
    };

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
}
