# Contrats d'API : ressources

## Knowledge

- [Doc : « Introduction », OpenAPI (learn.openapis.org)](https://learn.openapis.org/introduction.html)
  Définit le contrat comme une description d'API lisible par une machine, et ce qu'elle apporte (documentation, validation, code généré). Use for : ce qu'est un contrat, pourquoi l'écrire.
- [Doc : « HTTP response status codes », MDN](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status)
  Référence des codes de réponse (201, 202, 400, 401, 404). Use for : choisir ou relire un code d'erreur.
- [Article : « Tolerant Reader », Martin Fowler](https://www.martinfowler.com/bliki/TolerantReader.html)
  Comment un client survit à l'ajout de champs (il ignore ce qu'il ne lit pas). Use for : faire évoluer un contrat sans casser les clients.
- [Guide : « Microsoft Azure REST API Guidelines »](https://github.com/microsoft/api-guidelines/blob/vNext/azure/Guidelines.md)
  Recommandations d'un grand éditeur, avec des exemples de changements qui cassent (champ obligatoire ajouté ou retiré, valeurs d'énumération retirées). Use for : juger si un changement de contrat est cassant.

### Authentification (OAuth2)

- [Doc : « OAuth2 », Discord](https://docs.discord.com/developers/topics/oauth2)
  Le flux exact du projet : adresses, `code`, échange contre un token, `state`, scope `identify`. Use for : relire la config Discord.
- [Norme : RFC 6749, « The OAuth 2.0 Authorization Framework »](https://datatracker.ietf.org/doc/html/rfc6749)
  Les rôles (§1.1), le flux (§4.1), la confidentialité du secret client (§10.1), le `state` anti-CSRF (§10.12). Use for : trancher un doute sur ce qui est obligatoire.
- [Doc : « OAuth 2.0 Login », Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)
  Adresse de retour par défaut et appel du point d'accès aux infos utilisateur. Ne détaille pas `state` ni `OAuth2UserService`. Use for : comprendre ce que Spring fait à notre place.

## Wisdom (Communities)

Aucune pour l'instant. Pas de préférence exprimée : à proposer plus tard (forums Spring et Angular), sans insister.

## Gaps

- Aucune source sur le test de contrat entre le mod C# et l'API (type Pact) : à chercher quand on y arrivera.
- Pas de source sur la sécurité des API (OWASP API Security) : à ajouter pour une leçon sur les tokens et les identifiants dans les requêtes.
