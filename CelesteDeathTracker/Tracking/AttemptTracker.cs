namespace CelesteDeathTracker;

// Logique PURE : ce fichier ne doit référencer aucun type du jeu (Celeste, Monocle, Everest).
// Il est aussi compilé par CelesteDeathTracker.Tests, qui tourne en CI sans Celeste.

/// <summary>
/// État de la partie à un instant, réduit à des valeurs simples. <see cref="Session"/> est l'objet
/// Session du jeu, comparé seulement par référence (le redémarrage rapide le réutilise).
/// </summary>
internal sealed record GameSnapshot(
    object Session, string Chapter, string Side, string Room, long TimeTicks, int Deaths, int? SaveSlot);

/// <summary>Raisons de fin de tentative envoyées au serveur (cf. docs/data-model.md).</summary>
internal static class EndReasons
{
    public const string Completed     = "completed";
    public const string Restart       = "restart";
    public const string GoldenRestart = "golden_restart";
    public const string SaveAndQuit   = "save_and_quit";
    public const string GiveUp        = "give_up";
    public const string Switched      = "switched";
    public const string GameClosed    = "game_closed";
}

/// <summary>
/// Une tentative = une partie sur une face de map, de l'entrée à la sortie (terminée, recommencée,
/// abandonnée ou quittée). Une mort NE la termine PAS : le jeu recharge le niveau mais la partie
/// continue, c'est le serveur qui sait qu'un deathless est perdu à la première mort.
/// </summary>
internal sealed class Attempt
{
    public Attempt(string id, GameSnapshot start)
    {
        Id = id; Chapter = start.Chapter; Side = start.Side; SaveSlot = start.SaveSlot;
        Session = start.Session;
        Touch(start);
    }

    public string Id { get; }
    public string Chapter { get; }
    public string Side { get; }
    public int? SaveSlot { get; }
    public object Session { get; }
    public long LastTimeTicks { get; private set; }
    public int LastDeaths { get; private set; }
    public string LastRoom { get; private set; } = "";

    /// <summary>Mémorise l'état de la session pour pouvoir clore la tentative avec ses vraies valeurs.</summary>
    public void Touch(GameSnapshot snapshot)
    {
        LastTimeTicks = snapshot.TimeTicks;
        LastDeaths = snapshot.Deaths;
        LastRoom = snapshot.Room;
    }

    public bool IsSameMap(GameSnapshot snapshot) => Chapter == snapshot.Chapter && Side == snapshot.Side;

    /// <summary>
    /// Le redémarrage rapide (touche « recommencer » après une mort) ne déclenche PAS OnExit et
    /// réutilise le même objet Session en remettant son temps et ses morts à zéro : le seul signal
    /// fiable est donc qu'une de ces valeurs RECULE. Un respawn après une mort les laisse monter.
    /// </summary>
    public bool WentBackwards(GameSnapshot snapshot) =>
        !ReferenceEquals(snapshot.Session, Session) ||
        snapshot.TimeTicks < LastTimeTicks ||
        snapshot.Deaths < LastDeaths;
}

/// <summary>
/// Résultat d'une observation : la tentative en cours, si elle vient de commencer, et celle qui a
/// éventuellement été close au passage (avec la raison de sa fin).
/// </summary>
internal sealed record AttemptChange(Attempt Current, bool Started, Attempt? Ended, string? EndReason);

/// <summary>Suit la tentative en cours à partir des états successifs de la partie.</summary>
internal sealed class AttemptTracker
{
    private readonly Func<string> _newId;
    private readonly object _gate = new();
    private Attempt? _current;

    public AttemptTracker(Func<string>? newId = null)
    {
        _newId = newId ?? (() => Guid.NewGuid().ToString());
    }

    public Attempt? Current
    {
        get { lock (_gate) return _current; }
    }

    /// <summary>
    /// Renvoie la tentative en cours pour cet état, en en ouvrant une si besoin : première observation,
    /// session remise à zéro sur la même face (« restart »), ou autre map/face sans sortie
    /// intermédiaire (« switched »). L'ancienne tentative garde SES dernières valeurs (room et temps
    /// de la mort, par exemple), pas celles de la session déjà réinitialisée.
    /// </summary>
    public AttemptChange Observe(GameSnapshot snapshot)
    {
        lock (_gate)
        {
            var current = _current;
            if (current is not null && current.IsSameMap(snapshot) && !current.WentBackwards(snapshot))
            {
                current.Touch(snapshot);
                return new AttemptChange(current, Started: false, Ended: null, EndReason: null);
            }

            var reason = current is null ? null
                : current.IsSameMap(snapshot) ? EndReasons.Restart
                : EndReasons.Switched;

            var started = new Attempt(_newId(), snapshot);
            _current = started;
            return new AttemptChange(started, Started: true, Ended: current, EndReason: reason);
        }
    }

    /// <summary>Termine la tentative en cours (sortie de la map, fermeture du jeu…) et la renvoie.</summary>
    public Attempt? Close()
    {
        lock (_gate)
        {
            var current = _current;
            _current = null;
            return current;
        }
    }
}
