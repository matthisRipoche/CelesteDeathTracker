namespace CelesteDeathTracker.Tests;

/// <summary>
/// Scénarios de jeu vérifiés en partie (cf. CLAUDE.md, « Hooks ») et rejoués ici sans Celeste.
/// Le temps est en ticks .NET, comme Session.Time.
/// </summary>
public class AttemptTrackerTests
{
    private const string City = "Celeste/1-ForsakenCity";
    private const string Ridge = "Celeste/4-GoldenRidge";
    private const long OneSecond = TimeSpan.TicksPerSecond;

    // Ids prévisibles : "a1", "a2"…
    private static AttemptTracker NewTracker()
    {
        var n = 0;
        return new AttemptTracker(() => $"a{++n}");
    }

    private static GameSnapshot At(object session, string room, long time, int deaths,
        string chapter = City, string side = "A", int? saveSlot = 0) =>
        new(session, chapter, side, room, time, deaths, saveSlot);

    [Fact]
    public void PremiereObservationOuvreUneTentativeSansEnFermer()
    {
        var tracker = NewTracker();

        var change = tracker.Observe(At(new object(), "1", 0, 0));

        Assert.True(change.Started);
        Assert.Null(change.Ended);
        Assert.Null(change.EndReason);
        Assert.Equal("a1", change.Current.Id);
        Assert.Same(change.Current, tracker.Current);
    }

    [Fact]
    public void RespawnApresUneMortContinueLaMemeTentative()
    {
        var tracker = NewTracker();
        var session = new object();
        tracker.Observe(At(session, "1", 0, 0));

        // Mort puis rechargement du niveau : le temps et les morts montent.
        var change = tracker.Observe(At(session, "1", 5 * OneSecond, 1));

        Assert.False(change.Started);
        Assert.Null(change.Ended);
        Assert.Equal("a1", change.Current.Id);
        Assert.Equal(1, change.Current.LastDeaths);
    }

    [Fact]
    public void ChangementDeRoomMemoriseLaDerniereRoom()
    {
        var tracker = NewTracker();
        var session = new object();
        tracker.Observe(At(session, "1", 0, 0));

        tracker.Observe(At(session, "2", 3 * OneSecond, 0));

        Assert.Equal("2", tracker.Current!.LastRoom);
        Assert.Equal(3 * OneSecond, tracker.Current.LastTimeTicks);
    }

    [Fact]
    public void RedemarrageRapideFermeLaTentativeAvecSesDernieresValeurs()
    {
        var tracker = NewTracker();
        var session = new object();
        tracker.Observe(At(session, "1", 0, 0));
        tracker.Observe(At(session, "3", 40 * OneSecond, 2));

        // Le redémarrage rapide réutilise le même objet Session et remet temps et morts à zéro.
        var change = tracker.Observe(At(session, "1", 0, 0));

        Assert.True(change.Started);
        Assert.Equal(EndReasons.Restart, change.EndReason);
        Assert.Equal("a1", change.Ended!.Id);
        Assert.Equal("3", change.Ended.LastRoom);              // pas la room de la session réinitialisée
        Assert.Equal(40 * OneSecond, change.Ended.LastTimeTicks);
        Assert.Equal("a2", change.Current.Id);
    }

    [Fact]
    public void SeulLeTempsQuiReculeSuffitADetecterUnRedemarrage()
    {
        var tracker = NewTracker();
        var session = new object();
        tracker.Observe(At(session, "1", 10 * OneSecond, 0));

        var change = tracker.Observe(At(session, "1", 2 * OneSecond, 0));

        Assert.Equal(EndReasons.Restart, change.EndReason);
    }

    [Fact]
    public void NouvelObjetSessionSurLaMemeFaceEstUnRedemarrage()
    {
        var tracker = NewTracker();
        tracker.Observe(At(new object(), "1", 10 * OneSecond, 1));

        // Même valeurs croissantes, mais le jeu a créé une nouvelle Session sans passer par une sortie.
        var change = tracker.Observe(At(new object(), "1", 20 * OneSecond, 2));

        Assert.True(change.Started);
        Assert.Equal(EndReasons.Restart, change.EndReason);
    }

    [Fact]
    public void AutreMapSansSortieEstUnChangement()
    {
        var tracker = NewTracker();
        tracker.Observe(At(new object(), "1", 0, 0));

        var change = tracker.Observe(At(new object(), "a-00", 0, 0, chapter: Ridge));

        Assert.Equal(EndReasons.Switched, change.EndReason);
        Assert.Equal(City, change.Ended!.Chapter);
        Assert.Equal(Ridge, change.Current.Chapter);
    }

    [Fact]
    public void AutreFaceDeLaMemeMapEstUnChangement()
    {
        var tracker = NewTracker();
        var session = new object();
        tracker.Observe(At(session, "1", 0, 0));

        var change = tracker.Observe(At(session, "1", 0, 0, side: "B"));

        Assert.Equal(EndReasons.Switched, change.EndReason);
        Assert.Equal("B", change.Current.Side);
    }

    [Fact]
    public void CloseRendLaTentativeEtRepartDeZero()
    {
        var tracker = NewTracker();
        var session = new object();
        tracker.Observe(At(session, "1", 0, 0));

        var closed = tracker.Close();
        var change = tracker.Observe(At(session, "1", 5 * OneSecond, 0));

        Assert.Equal("a1", closed!.Id);
        Assert.True(change.Started);
        Assert.Null(change.Ended);    // déjà close : rien à refermer
        Assert.Equal("a2", change.Current.Id);
    }

    [Fact]
    public void CloseSansTentativeRendNull()
    {
        Assert.Null(NewTracker().Close());
    }

    [Fact]
    public void LaSauvegardeEstCelleDuDebutDeTentative()
    {
        var tracker = NewTracker();
        var session = new object();
        tracker.Observe(At(session, "1", 0, 0, saveSlot: 2));

        var change = tracker.Observe(At(session, "2", OneSecond, 0, saveSlot: null));

        Assert.Equal(2, change.Current.SaveSlot);
    }
}
