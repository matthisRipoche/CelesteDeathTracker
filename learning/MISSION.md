# Mission : valider les contrats d'API proposés par l'IA

## Why
Apprendre le développement assisté par l'IA : cadrer, décider, relire, sans écrire Angular ou Spring à la main.
Claude propose les contrats d'API du projet CelesteDeathTracker (routes, corps JSON, codes d'erreur) ;
l'utilisateur doit savoir les relire pour les accepter, les corriger ou les refuser en connaissance de cause.

## Success looks like
- Relire un contrat proposé et poser les quatre bonnes questions : qui appelle, ce qui entre et sort, ce qui se passe en cas d'erreur, comment il évolue.
- Distinguer un changement qui casse un client (renommer, retirer, rendre obligatoire) d'un changement sans danger (ajouter un champ facultatif).
- Repérer dans une proposition ce qui ne doit pas être là (par exemple un identifiant d'utilisateur dans le corps d'une requête du mod).

## Constraints
- Tous les exemples viennent du projet réel (mod C#, API Spring Boot, site Angular).
- Leçons courtes, en français, avec une vérification immédiate des acquis.

## Out of scope
- Écrire du code Spring ou Angular à la main.
- Concevoir une API REST complète from scratch, GraphQL, gRPC, versionnement avancé.
