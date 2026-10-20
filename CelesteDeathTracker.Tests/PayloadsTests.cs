using System.Text.Json;

namespace CelesteDeathTracker.Tests;

/// <summary>Contrat JSON avec l'API (cf. docs/data-model.md) : noms des champs et champs facultatifs.</summary>
public class PayloadsTests
{
    private static JsonElement ToJson<T>(T value) =>
        JsonDocument.Parse(JsonSerializer.Serialize(value, PayloadJson.Options)).RootElement;

    [Fact]
    public void UneMortEnvoieTousLesChampsObligatoiresEnCamelCase()
    {
        var json = ToJson(new DeathEvent
        {
            Timestamp = 1, Chapter = "Celeste/1-ForsakenCity", Room = "1",
            DeathX = 10, DeathY = 20, TotalDeaths = 3, SessionDeaths = 2, TimeInRoomMs = 500
        });

        // Jackson 3 côté API refuse un JSON auquel il manque un champ numérique : tous doivent partir.
        foreach (var name in new[] { "timestamp", "chapter", "room", "deathX", "deathY",
                                     "totalDeaths", "sessionDeaths", "timeInRoomMs" })
            Assert.True(json.TryGetProperty(name, out _), $"champ manquant : {name}");
    }

    [Fact]
    public void UneMortSansTentativeNEnvoiePasLesChampsDeTentative()
    {
        var json = ToJson(new DeathEvent { Chapter = "c", Room = "r" });

        Assert.False(json.TryGetProperty("attemptId", out _));
        Assert.False(json.TryGetProperty("side", out _));
        Assert.False(json.TryGetProperty("saveSlot", out _));
    }

    [Fact]
    public void LEvenementDeTentativeReprendLaTentativeEtConvertitLeTemps()
    {
        var attempt = new Attempt("a1",
            new GameSnapshot(new object(), "Celeste/1-ForsakenCity", "B", "1", 0, 0, SaveSlot: 1));

        var evt = AttemptEvent.For("attempt_end", attempt, "3", sessionTicks: 12 * TimeSpan.TicksPerSecond,
            timestamp: 42, reason: EndReasons.Restart);

        Assert.Equal("a1", evt.AttemptId);
        Assert.Equal("Celeste/1-ForsakenCity", evt.Chapter);
        Assert.Equal("B", evt.Side);
        Assert.Equal(1, evt.SaveSlot);
        Assert.Equal("3", evt.Room);
        Assert.Equal(12_000, evt.SessionTimeMs);
        Assert.Equal(42, evt.Timestamp);
        Assert.Equal("restart", evt.Reason);
    }

    [Fact]
    public void LesChampsFacultatifsAbsentsNeSontPasEnvoyes()
    {
        var attempt = new Attempt("a1", new GameSnapshot(new object(), "c", "A", "1", 0, 0, SaveSlot: null));

        var json = ToJson(AttemptEvent.For("room_enter", attempt, "2", 0, 0));

        Assert.Equal("room_enter", json.GetProperty("type").GetString());
        Assert.Equal("a1", json.GetProperty("attemptId").GetString());
        Assert.True(json.TryGetProperty("sessionTimeMs", out _));
        foreach (var name in new[] { "mapName", "startedFromBeginning", "reason", "saveSlot" })
            Assert.False(json.TryGetProperty(name, out _), $"champ inattendu : {name}");
    }
}
