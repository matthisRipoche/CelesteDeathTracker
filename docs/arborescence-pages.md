# Arborescence des pages

Refonte du site autour de l'objectif décidé dans [`donnees-du-jeu.md`](donnees-du-jeu.md) : **accompagner
les tentatives deathless**, par face de map. L'unité n'est plus la mort mais la **tentative** (voir
[`data-model.md`](data-model.md)).

## Décisions prises

| Sujet | Choix |
|---|---|
| Unité du site | La tentative, regroupée par **face de map** (une face = map + A/B/C) |
| Total de morts | Supprimé de l'interface |
| Direct | Page `/live` **et** bandeau global dans la coque quand une tentative est en cours |
| Navigation | 4 entrées : Tableau de bord, Maps, Historique, Direct. Compte et Appareils sous le menu utilisateur |
| Style | Design system **Topo**, clair et sombre (voir `design-system.md`), validé sur les maquettes |
| Maquettes | Canvas Claude Design « Death Tracker, maquettes » : design system, détail d'une face et tableau de bord, en clair et en sombre |
| Première tranche à construire | Le détail d'une face (route d'API + page) |

## Arbre des pages

```
/login                       Connexion et inscription                  (public)
/pair                        Associer un jeu                           (URL imposée : le mod l'ouvre)

/                            Tableau de bord
/maps                        Mes maps                                  (une carte par face jouée)
/maps/:side?sid=             Détail d'une face de map                  ← page centrale (faite)
/maps/:side/:room?sid=      Détail d'une room
/attempts                    Historique des tentatives                 (filtres)
/attempts/:id                Détail d'une tentative
/live                        Direct (fait)

/account                     Compte
/account/devices             Appareils                                 (l'ancien /tokens)
```

- `sid` (paramètre de requête, car le SID contient des « / ») est `Celeste/1-ForsakenCity` ; `:side` vaut `a`, `b` ou `c` ; `:room` est le nom
  de la room tel que le jeu l'envoie (`6a`, `b-05`). Une room s'identifie par (map, face, room).
- `/pair` ne change pas : le mod ouvre cette adresse (`app.public-url` + `/pair`).
- Redirections des anciennes adresses : `/tokens` → `/account/devices`, `/profile` → `/account`,
  `/map?sid=…` → `/maps`.

### Navigation

- **Barre latérale (bureau, fait)** : fixe à gauche sur toute la hauteur, avec la marque, Tableau de bord · Maps ·
  Historique · Direct, puis en bas la photo de profil (avatar Discord, sinon l'initiale) et le pseudo. Un clic ouvre
  le menu du compte : Compte, Appareils, Déconnexion. Le contenu occupe toute la largeur restante, sans
  plafond. « Associer un jeu » n'est plus dans la navigation : il est dans Appareils et dans l'état vide du
  tableau de bord.
- **Barre du bas (mobile, fait)** : Accueil · Maps · Historique · Direct · Compte (avatar). La déconnexion est
  sur la page Compte.
- **Bandeau de direct** : visible sur toutes les pages connectées tant qu'une tentative est ouverte
  (« En jeu : Forsaken City · B, room 6a, 00:42 »), il mène à `/live`.
- Les pages de détail ont un fil d'Ariane : Maps › Forsaken City · B › 6a.

## Contenu de chaque page

### Tableau de bord `/`
Répond à « où en suis-je aujourd'hui ? ».
- **Reprendre là où j'en suis** : la dernière face jouée, avec sa meilleure progression et un lien.
- Chiffres du jour : tentatives, deathless réussis, meilleure tentative (durée), face la plus travaillée.
- **Maps récentes** : 3 faces avec une barre de progression (rooms atteintes / rooms de la map).
- Activité : tentatives par jour sur 14 jours.
- État vide (première visite) : les trois étapes d'appairage (lancer le jeu, saisir le code, jouer).

### Mes maps `/maps`
- Une carte par **face jouée** : nom officiel, face (A/B/C), mod, nombre de tentatives, deathless
  réussis, meilleure progression, dernière partie.
- Tri : plus récentes, plus travaillées, A à Z. Recherche et filtre par mod.

### Détail d'une face `/maps/:side?sid=` (fait)
La page centrale. Répond à « où mon deathless casse-t-il ? ».
- En-tête : nom officiel, face, mod, lien vers l'historique filtré.
- Chiffres : tentatives, deathless réussis, taux de réussite, meilleure progression, tentative la plus
  longue.
- **Où mes tentatives s'arrêtent** : une barre par room, dans l'ordre de la map, avec le nombre de
  tentatives qui se terminent là (première mort ou abandon). Chaque ligne mène à la page de la room.
- **Évolution** : durée (ou progression) de chaque tentative dans le temps, pour voir si on progresse.
- Dix dernières tentatives (résultat, durée, room de fin).

### Détail d'une room `/maps/:side/:room?sid=` (fait)
Répond à « à quel endroit précis mon deathless casse-t-il, et est-ce que je passe cette room de plus en plus souvent ? ».
- Chiffres : tentatives arrivées dans la room, arrêts, **taux d'arrêt** (arrêts / arrivées, toujours entre
  0 et 100 %), total de morts et date de la dernière.
- **Nuage de points** des morts aux coordonnées du jeu. Les premières morts de la tentative (celles qui
  font perdre le deathless) sont pleines, les suivantes creuses. Le cadre vient des morts elles-mêmes
  (le jeu n'envoie pas les limites de la room), avec la même échelle sur X et Y. La heatmap de densité
  viendra quand il y aura plus de données.
- **Tes passages** : une case par tentative arrivée dans la room (franchie, morte ici, terminée ici sans
  mourir), de la plus ancienne à la plus récente, avec une tendance « premières 10 / dernières 10 ».
- Dernières morts (10), avec leur position.
- Liens vers la room précédente et la suivante ; chaque room de la page de la face mène ici.

### Historique `/attempts` (fait)
- Liste paginée de toutes les tentatives significatives (voir règle plus bas), 20 par page, de la plus récente à la plus ancienne.
- Filtres : face (`?sid=&side=`, depuis le lien de la page d'une face), résultat (réussie, morte, abandonnée, en cours), période prédéfinie (7 jours, 30 jours, depuis toujours).
- Ligne : date (lien vers le détail), face, durée, résultat, room de fin, nombre de morts.

### Détail d'une tentative `/attempts/:id` (fait)
- Résultat et durée de la tentative.
- **Parcours** : les rooms traversées dans l'ordre, avec le temps de partie à chaque entrée.
- Morts de la tentative : room, position, temps.
- Liens vers la face et vers la room où elle s'est terminée.

### Direct `/live`
- Tentative en cours : face, room actuelle, chrono, nombre de morts.
- Flux des dernières tentatives terminées (résultat en direct).
- Alimenté par SSE (`SseEmitter`), par utilisateur.

### Compte `/account` et Appareils `/account/devices`
- Compte : email, date d'inscription, mot de passe, export des données, suppression du compte.
- Appareils : liste des appareils associés (nom, serveur, dernier envoi), révocation, bouton
  « Associer un jeu » (mène à `/pair`).

## Règles de calcul communes

- **Tentative significative** : au moins une mort, une entrée de room, ou une fin `completed`. Les
  tentatives de quelques secondes sans rien d'autre (recommencer tout de suite) sont ignorées dans toutes
  les listes et statistiques.
- **Deathless réussi** : `end_reason = completed` et aucune mort rattachée.
- **Tentative terminée par** : `completed`, `restart`, `golden_restart`, `give_up`, `save_and_quit`,
  `switched`, ou en cours (pas de fin, session récente) ; une tentative ouverte depuis trop longtemps
  est considérée comme abandonnée.
- **Room où s'arrête une tentative** : la room de la première mort ; à défaut, la room de fin.
- **Ordre des rooms d'une map** : déduit des `room_entries` de la meilleure tentative (ou de la première
  tentative terminée) ; les rooms jamais vues ne sont pas affichées.
- **Tentative arrivée dans une room** : elle y est entrée, s'y est arrêtée ou y est morte. Un événement
  d'entrée perdu (envoi sans file d'attente) ne la fait donc pas disparaître. Le nombre d'arrivées peut
  dépasser celui de la page de la face dans ce seul cas.
- **Taux d'arrêt d'une room** : tentatives qui s'y arrêtent (mort ou abandon) / tentatives qui y arrivent.
  Toujours entre 0 et 100 %. On n'utilise pas « morts / entrées » : après une mort le jeu recharge la room
  sans nouvelle entrée, donc plusieurs morts pour une entrée peuvent dépasser 100 %. Le total de morts
  reste affiché à côté.

## Données à fournir par l'API

Toutes les routes sont filtrées par compte, comme `GET /api/deaths` aujourd'hui.

| Route | Sert à |
|---|---|
| `GET /api/faces` (fait) | Mes maps : une ligne par face, avec les chiffres de la carte |
| `GET /api/faces/detail?sid=&side=` (fait) | Détail d'une face : chiffres, rooms dans l'ordre avec le nombre de fins, évolution |
| `GET /api/faces/room?sid=&side=&room=` (fait) | Détail d'une room : arrivées, arrêts, taux d'arrêt, positions des morts (500 dernières), tentatives (100 dernières), rooms voisines |
| `GET /api/attempts?sid=&side=&result=&period=&page=&size=` (fait) | Historique : `{items, page, size, total}`. `period` = `7d`, `30d` ou `all` (défaut) ; `page` à partir de 0 ; `size` de 1 à 100 (défaut 20) ; paramètre invalide = 400. Pagination en mémoire (le classement dépend des morts et des entrées de room) |
| `GET /api/attempts/{id}` (fait) | Détail d'une tentative : résumé, `route` (rooms dans l'ordre avec le temps de partie) et `deaths`. 404 si elle n'existe pas, n'est pas à ce compte ou n'est pas significative |
| `GET /api/summary` | Tableau de bord : reprendre, chiffres du jour, maps récentes, activité |
| `GET /api/live` (SSE) | Direct : tentative en cours et flux |

Les statistiques se calculent côté serveur en SQL (`GROUP BY`), pas dans le navigateur comme le fait
le tableau de bord actuel.

## Écarts avec le site actuel

| Aujourd'hui | Après la refonte |
|---|---|
| Tableau de bord = total de morts, camembert, classement des rooms, dernières morts | Tableau de bord = progression et tentatives du jour |
| `/maps` = une carte par map, nombre de morts | Une carte par **face**, tentatives et réussites |
| `/map?sid=` = rooms et nuage de points | `/maps/:side?sid=` puis `/maps/:side/:room?sid=` |
| `/profile` et `/tokens` en barre ou page isolée | `/account` et `/account/devices` sous le menu utilisateur (fait, anciennes adresses redirigées) |
| Calculs dans le navigateur depuis `GET /api/deaths` | Routes de statistiques côté Spring |
| Pas de direct | `/live` et bandeau global |

## Ordre de construction

1. **Maquettes** : faites pour le détail d'une face et le tableau de bord (canvas Claude Design). Restent
   à dessiner : Mes maps, détail d'une room, Historique, détail d'une tentative, Direct, Appareils, Compte,
   connexion, et la version mobile.
   Le style est déjà appliqué à l'application (`design-system.md`) ; les pages gardent leur ancienne mise
   en page jusqu'à leur refonte.
2. Détail d'une face (route + page) : c'est la page dont toutes les autres sont un résumé.
3. Mes maps, puis détail d'une room, puis Historique et détail d'une tentative.
4. Tableau de bord.
5. Direct (SSE) et bandeau.
6. Compte et Appareils (déplacement des pages existantes).
