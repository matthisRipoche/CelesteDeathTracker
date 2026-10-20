using Celeste.Mod;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace CelesteDeathTracker;

/// <summary>
/// Envoi des événements du jeu à l'API d'ingestion. Fire-and-forget : timeout court, erreurs
/// journalisées sans bruit, le jeu ne doit jamais être bloqué ou planté par le serveur.
/// </summary>
internal static class ApiClient
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(2) };

    public static async Task PostAsync<T>(string path, T data)
    {
        // On fige le serveur au moment de l'envoi : si le joueur en change pendant la requête,
        // la réponse (ex. 401) doit être attribuée à celui qui l'a émise.
        var target = DeathTrackerModule.Settings.Server;
        var token = Servers.TokenFor(target);

        // Sans token le serveur répondrait 401 : inutile d'envoyer quoi que ce soit.
        if (token.Length == 0)
            return;

        var url = Servers.UrlFor(target) + path;
        try
        {
            var json = JsonSerializer.Serialize(data, PayloadJson.Options);
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
                    $"Token refusé par le serveur {Servers.NameOf(target)} : nouvelle association.");
                PairingManager.Reset(target);
            }
            else if (!response.IsSuccessStatusCode)
            {
                Logger.Log(LogLevel.Warn, "DeathTracker",
                    $"Le serveur {Servers.NameOf(target)} a refusé l'envoi ({(int)response.StatusCode}).");
            }
        }
        catch (Exception ex)
        {
            Logger.Log(LogLevel.Verbose, "DeathTracker", $"Envoi échoué vers {url} : {ex.Message}");
        }
    }
}
