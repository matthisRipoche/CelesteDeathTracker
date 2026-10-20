using System.Text.Json;
using System.Text.Json.Serialization;

namespace CelesteDeathTracker;

// Logique PURE : ce fichier ne doit référencer aucun type du jeu (Celeste, Monocle, Everest).
// Il est aussi compilé par CelesteDeathTracker.Tests, qui tourne en CI sans Celeste.

/// <summary>Format JSON attendu par l'API : camelCase, champs facultatifs absents non envoyés.</summary>
internal static class PayloadJson
{
    public static readonly JsonSerializerOptions Options = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        // Les champs facultatifs absents (mapName, reason…) ne sont pas envoyés
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    /// <summary>Le temps de session du jeu est en ticks .NET (100 ns).</summary>
    public static long TicksToMs(long ticks) => ticks / TimeSpan.TicksPerMillisecond;
}

/// <summary>
/// Une mort. Tous les champs numériques sont obligatoires côté API (Jackson 3 refuse un JSON
/// auquel il en manque un).
/// </summary>
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

    /// <summary>
    /// Les événements portent tous la face et la sauvegarde : le serveur reconstruit la tentative
    /// même si l'un d'eux se perd.
    /// </summary>
    internal static AttemptEvent For(string type, Attempt attempt, string room, long sessionTicks, long timestamp,
        string? mapName = null, bool? startedFromBeginning = null, string? reason = null) => new()
    {
        Type                 = type,
        AttemptId            = attempt.Id,
        Chapter              = attempt.Chapter,
        Side                 = attempt.Side,
        SaveSlot             = attempt.SaveSlot,
        Room                 = room,
        Timestamp            = timestamp,
        SessionTimeMs        = PayloadJson.TicksToMs(sessionTicks),
        MapName              = mapName,
        StartedFromBeginning = startedFromBeginning,
        Reason               = reason
    };
}
