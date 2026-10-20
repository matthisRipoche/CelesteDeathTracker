using Celeste.Mod;

namespace CelesteDeathTracker;

/// <summary>Serveur vers lequel les morts sont envoyées. Chaque serveur a son propre token.</summary>
public enum ServerTarget { Production, Local }

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

    // Remplis automatiquement par l'association (voir Pairing/). Un token par serveur : passer
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

    /// <summary>
    /// Anciennes versions : une seule adresse (ServerUrl) et un seul token (ApiToken). On range le
    /// token sous le bon serveur pour que personne n'ait à se ré-associer, puis on vide l'ancien champ.
    /// Renvoie vrai si les réglages ont changé et doivent être sauvegardés.
    /// </summary>
    internal bool MigrateLegacy()
    {
        var legacyToken = (ApiToken ?? "").Trim();
        var legacyUrl = (ServerUrl ?? "").Trim();
        if (legacyToken.Length == 0 && legacyUrl.Length == 0)
            return false;

        if (legacyToken.Length > 0)
        {
            var isLocal = legacyUrl.Length == 0 || Servers.IsLocalUrl(legacyUrl);
            if (isLocal && LocalToken.Trim().Length == 0) LocalToken = legacyToken;
            if (!isLocal && ProductionToken.Trim().Length == 0) ProductionToken = legacyToken;
            Logger.Log(LogLevel.Info, "DeathTracker",
                $"Réglages migrés : ancien token rangé sous « {(isLocal ? "Local" : "Production")} ».");
        }

        ApiToken = "";
        ServerUrl = "";
        return true;
    }
}

/// <summary>Adresse, nom et token de chaque serveur, lus dans les réglages.</summary>
internal static class Servers
{
    private static DeathTrackerSettings Settings => DeathTrackerModule.Settings;

    public static string NameOf(ServerTarget target) => target == ServerTarget.Production ? "Production" : "Local";

    public static string UrlFor(ServerTarget target) =>
        (target == ServerTarget.Production ? Settings.ProductionUrl : Settings.LocalUrl).Trim().TrimEnd('/');

    public static string TokenFor(ServerTarget target) =>
        (target == ServerTarget.Production ? Settings.ProductionToken : Settings.LocalToken).Trim();

    public static void SetToken(ServerTarget target, string token)
    {
        if (target == ServerTarget.Production) Settings.ProductionToken = token;
        else Settings.LocalToken = token;
    }

    public static bool IsLocalUrl(string url) =>
        Uri.TryCreate(url, UriKind.Absolute, out var u) &&
        (u.IsLoopback || u.Host.Equals("localhost", StringComparison.OrdinalIgnoreCase));
}
