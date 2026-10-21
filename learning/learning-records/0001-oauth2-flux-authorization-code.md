# 0001 : OAuth2, flux « authorization code »

**Date** : 2026-10-21 · **Leçon** : `lessons/0002-comprendre-oauth2-discord.html`

## Ce qui a été présenté
- Les trois rôles (joueur, ton API = client, Discord), les 6 étapes du flux, ce qui ne passe jamais par le navigateur (secret client, access token).
- `state` (anti-CSRF), adresse de retour identique des deux côtés, scope minimal (`identify`).
- Le rôle de `DiscordOAuth2UserService` et le détour `POST /api/auth/discord/link` pour rattacher.

## À vérifier au prochain échange
- L'utilisateur sait-il dire seul pourquoi le secret client ne peut pas être dans Angular ?
- Le point « `state` est géré par Spring » n'a pas été vérifié dans la doc lue : à confirmer dans le code ou la doc de Spring.

## Prochaine étape possible
- Le cas d'échec : Refuser chez Discord, ou compte Discord déjà lié à un autre compte (`AlreadyLinkedException`).
- Comparer avec les tokens d'API du mod (Bearer) : deux mécanismes différents pour deux clients différents.
