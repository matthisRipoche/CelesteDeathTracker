# Les données du jeu : ce que le mod envoie, ce que ça veut dire, ce qu'on en tire

Ce document sert de base à la refonte du site. Il répond à trois questions :

1. **Qu'est-ce que le jeu nous renvoie**, champ par champ, et que représente chaque valeur ?
2. **Que peut-on calculer** à partir de ces champs sans rien changer au mod ?
3. **Quelles données sont les plus intéressantes** à montrer, et lesquelles manquent ?

Le contrat technique (types, format JSON) reste dans [`data-model.md`](data-model.md). Ici on
explique le *sens* des données et leurs limites.

> **Sources.** Les sections « Constaté » viennent du code du mod
> (`CelesteDeathTracker/DeathTrackerModule.cs`) et des 23 morts réelles de
> `dashboard-legacy/deaths.json`. Les sections « À vérifier en jeu » reposent sur ma connaissance
> de Celeste et d'Everest, pas sur une mesure : à confirmer avant de bâtir une page dessus.

## 0. Le but du site : accompagner les tentatives deathless

Le site n'est pas un compteur de morts, c'est un **compagnon de deathless** : réussir une face de
map sans mourir une seule fois. Ça change ce qui compte :

- **L'unité de base est la tentative**, pas la mort. Une tentative va de l'entrée dans la face de la
  map jusqu'à la première mort (échec) ou jusqu'à la fin de la map (réussite). La première mort
  met fin à la tentative : le joueur recommence la map depuis le début.
- Les questions du joueur sont : *« Jusqu'où j'arrive ? »*, *« Où mes tentatives s'arrêtent-elles ? »*,
  *« Est-ce que je progresse ? »*, *« Combien de temps je tiens ? »*.
- **Le nombre total de morts n'a pas de sens ici** : il mélange toutes les maps et toutes les
  sauvegardes, et une mort est un échec de tentative, pas un score. Il sort de l'interface (voir
  `totalDeaths`).
- Les stats sont **par face** (A, B ou C) d'une map **précise** : un deathless de la face A n'a rien
  à voir avec celui de la face B.

---

## 1. Ce que le mod envoie

Le mod accroche trois événements du jeu et envoie du JSON au serveur avec le token du joueur.

| Hook MonoMod | Moment | Envoi | Stocké ? |
|---|---|---|---|
| `On.Celeste.Player.Die` | Madeline meurt | `POST /api/ingest/deaths` → `DeathEvent` | **Oui** (table `death`) |
| `On.Celeste.Level.Begin` | Un niveau démarre | `POST /api/ingest/events` → `SessionEvent` `session_start` | **Non** (202 et jeté) |
| `On.Celeste.Level.End` | Un niveau se termine | `POST /api/ingest/events` → `SessionEvent` `session_end` | **Non** |

Aujourd'hui, **tout ce que le site peut afficher vient d'un seul type d'événement : la mort.** Or
une tentative réussie ne contient aucune mort : le site ne peut pas encore la voir.

### 1.1 `DeathEvent`, champ par champ

Exemple réel (première mort de `deaths.json`) :

```json
{
  "timestamp": 1779228631547,
  "chapter": "monikadsidespack/0/7-Summit",
  "room": "b-05",
  "deathX": 7002,
  "deathY": -2168,
  "totalDeaths": 35507,
  "sessionDeaths": 91,
  "timeInRoomMs": 1105697
}
```

#### `timestamp` — le moment de la mort

- **Représente** : l'instant de la mort, en millisecondes Unix (UTC), lu sur **l'horloge du PC du
  joueur** (`DateTimeOffset.UtcNow`).
- **Sert à** : tout ce qui est temporel (historique des tentatives, rythme, sessions, flux en
  direct).
- **Limites** : l'horloge est celle du client, donc falsifiable ou fausse (PC mal réglé). Le serveur
  ne pose pas sa propre heure de réception. Inoffensif pour des stats personnelles, à garder en
  tête pour des classements publics.
- **Constaté** : les 23 morts s'étalent sur 8 minutes, avec 3,5 s à 64 s entre deux morts
  (moyenne 21 s).

#### `chapter` — la map (SID complet)

- **Représente** : l'identifiant de la map, `level.Session.Area.GetSID()`.
  - Jeu de base : `Celeste/1-ForsakenCity`
  - Mod : `monikadsidespack/0/7-Summit` (levelSet / index / nom)
- **Sert à** : regrouper par map et par mod. `mod` et `map` en sont dérivés (premier et dernier
  segment).
- **Limites** :
  - Le nom du champ est trompeur : ce n'est pas un « chapitre » au sens du jeu mais la **map**.
  - **Le SID ne contient pas la face (A / B / C)** : `GetSID()` renvoie le SID seul, alors que la
    face est portée par `Area.Mode`. Les morts en face A, B et C d'une même map sont donc
    mélangées, ce qui est bloquant pour un suivi de deathless. *À vérifier en jouant une face B.*
  - **Pas de nom lisible** : « 7-Summit » est un identifiant, pas le titre affiché dans le jeu
    (« Le Sommet », « Forsaken City »…). Le site doit montrer le nom officiel.
- **Constaté** : 2 maps dans les données (`monikadsidespack/0/7-Summit` : 5 morts ;
  `MoonRuins/1-moonruins` : 18 morts).

#### `room` — la salle

- **Représente** : le nom de la room courante, `level.Session.Level` (`b-05`, `a-09`…). Une map est
  découpée en rooms ; un écran de jeu fait environ 320 × 180 pixels et une room en couvre un ou
  plusieurs.
- **Sert à** : voir *où* la tentative s'arrête, la heatmap, la progression.
- **Limites** :
  - **Le nom n'est pas unique d'une map à l'autre** : `b-05` existe à la fois dans `7-Summit` et
    dans `1-moonruins`. Toujours identifier une room par le couple **(`chapter`, `room`)** (et, une
    fois ajoutée, la face).
  - Le nom est celui choisi par l'auteur de la map : pas d'ordre garanti, pas de numéro de
    progression. Pour dire « jusqu'où j'arrive », il faudra connaître l'**ordre des rooms**.
  - On ne connaît ni la taille de la room ni sa position dans la map (voir `deathX`).
- **Constaté** : 10 couples (map, room) différents ; le plus meurtrier est
  (`7-Summit`, `b-05`) avec 5 morts. En regroupant par nom de room seul, `b-05` afficherait 8 morts
  (5 + 3) : deux salles différentes fusionnées par erreur.

#### `deathX`, `deathY` — l'endroit exact de la mort

- **Représente** : `Player.Position`, tronqué en entier. Ce sont des **pixels de jeu en
  coordonnées du monde de la map**, pas des coordonnées d'écran ni de room. L'axe **Y croît vers le
  bas** (convention de Celeste), d'où les valeurs négatives pour les zones au-dessus de l'origine.
- **Sert à** : la heatmap / le nuage de points (« à quel passage précis mon deathless casse-t-il ? »).
- **Limites** :
  - Coordonnées **absolues** dans la map : sans le cadre de la room (voir « Évolutions futures »),
    on ne peut pas dessiner la salle autour des points.
  - Le point est celui de Madeline (au niveau des pieds), pas celui du danger qui l'a tuée.
  - Valeurs très grandes sur de grosses maps : X de 1 734 à 12 450, Y de −2 223 à 2 661 dans nos
    données.
- **Constaté** : dans une même room (`7-Summit` / `b-05`), les 5 morts se regroupent bien (X 6 853
  à 7 012, Y −2 223 à −2 168) : le nuage de points désigne un passage précis.

#### `totalDeaths` — le compteur de morts de la sauvegarde (à ne pas afficher)

- **Représente** : `SaveData.Instance.TotalDeaths`, le compteur **du fichier de sauvegarde** du jeu,
  toutes maps et tous mods confondus, **y compris les morts d'avant l'installation du mod**.
- **Usage décidé** : **aucun affichage.** Pour un joueur qui vise un deathless, ce total n'a pas de
  sens (il additionne des maps sans rapport). Il ne reste utile qu'**en interne** : deux morts
  consécutives d'une même sauvegarde devraient différer de 1, donc un écart plus grand révèle des
  envois **perdus** (le mod envoie en fire-and-forget avec 2 s de délai).
- **Constaté** : les 23 lignes portent des `totalDeaths` à 35 507 (`Summit`) et ~2 742
  (`MoonRuins`) : deux sauvegardes différentes. Le mod ne transmet pas d'identifiant de sauvegarde,
  donc on ne peut pas les séparer.

#### `sessionDeaths` — les morts de la partie en cours

- **Représente** : `level.Session.Deaths`, le nombre de morts depuis que la map a été (re)lancée.
  La « session » est ici celle du jeu (une partie sur une face), pas une session web. Elle repart
  de zéro quand on recommence la map depuis le début.
- **Sert à** : **repérer la fin d'une tentative deathless** : `sessionDeaths = 1` signifie que c'est
  la première mort de la partie, donc la tentative s'arrête là. Les valeurs suivantes (2, 3…)
  indiquent que le joueur a continué à s'entraîner après l'échec.
- **Limites** : si on quitte puis reprend une map depuis la sauvegarde, la session peut être
  restaurée avec son compteur : ne pas supposer qu'il retombe toujours à 0. *À vérifier en jeu* :
  que « recommencer le chapitre » crée bien une nouvelle session à 0.
- **Constaté** : de 1 à 95. Les données actuelles sont de l'**entraînement** (jusqu'à 18 morts de
  suite dans la même partie sur `MoonRuins`), pas des tentatives deathless.

#### `timeInRoomMs` — le temps de la partie… et non de la room

- **Représente** : `level.Session.Time / 10 000`, c'est-à-dire le **temps cumulé de la partie** en
  millisecondes (le jeu compte en ticks de 100 ns). Contrairement à ce que le nom suggère, **ce
  n'est pas le temps passé dans la room**.
- **Sert à** : pour une première mort (`sessionDeaths = 1`), c'est **la durée de la tentative** :
  combien de temps le joueur a tenu avant d'échouer.
- **Limites** :
  - Le nom du champ est faux. Il est figé dans le contrat (base, API, mod) : le renommer demande
    une migration Flyway et une version du mod. À défaut, ne jamais l'afficher comme « temps dans
    la room ».
  - Il ne compte que le temps de jeu actif (pas les menus ni la pause).
- **Constaté** : la différence entre deux morts consécutives colle à la différence de `timestamp`
  (65,2 s contre 63,9 s ; 5,0 s contre 5,0 s), preuve que c'est bien un chronomètre de partie. Il
  retombe à 2,4 s quand on change de map (−1 202 s entre `Summit` et `MoonRuins`). Maximum
  observé : 20 minutes.

### 1.2 `SessionEvent` (envoyé mais pas conservé)

| Champ | Représente |
|---|---|
| `type` | `session_start` (hook `Level.Begin`) ou `session_end` (hook `Level.End`) |
| `chapter` | SID de la map |
| `room` | Room au moment de l'événement |
| `timestamp` | Instant, horloge du client |

- **Constaté** : le serveur répond 202 puis **jette** l'événement.
- **À vérifier en jeu** : dans Celeste, la mort recharge le niveau en créant un nouvel objet
  `Level`. Si c'est bien le cas, `Level.Begin`/`Level.End` partent **à chaque mort** (et à chaque
  retour de pause), pas seulement à l'entrée et à la sortie de la map. Ils ne mesureraient alors
  pas des tentatives.
- **Enjeu pour le deathless** : ce sont ces événements qu'il faudrait pour compter les tentatives
  **réussies** (début de la map, fin de la map sans mort). Il faudra donc des événements fiables de
  *début de tentative* et de *complétion*, par exemple via `Everest.Events.Level.OnEnter` /
  `OnComplete`, ou en observant l'entrée dans la map et le hook de fin de chapitre.

---

## 2. Ce qu'on peut déjà calculer (sans toucher au mod)

Tout se déduit des 8 champs d'un `DeathEvent`. « Affiché » désigne le site actuel.

| Indicateur | Calcul | Affiché ? |
|---|---|---|
| **Où mes tentatives s'arrêtent** | Rooms des morts avec `sessionDeaths = 1` | Non |
| **Durée de chaque tentative** | `timeInRoomMs` des morts avec `sessionDeaths = 1` | Non |
| **Meilleure tentative** (celle qui a tenu le plus longtemps) | `MAX(timeInRoomMs)` parmi les premières morts | Non |
| **Tendance** : est-ce que je tiens plus longtemps qu'avant ? | Durée des tentatives dans le temps | Non |
| Morts par jour / par heure / par jour de la semaine | `GROUP BY` sur `timestamp` | Par jour seulement |
| Rooms les plus meurtrières (de la map) | `GROUP BY (chapter, room)` | Oui |
| **Heatmap** de densité par room | Grille de cases (par ex. 8 px = une tuile) | Nuage de points seulement |
| Série en cours : morts d'affilée dans la même room | Morts consécutives au même (map, room) | Non |
| Morts par map | `GROUP BY chapter` | Oui |
| Dernières morts | `ORDER BY timestamp DESC` | Oui |
| Morts manquées (qualité des données) | `totalDeaths[n] − totalDeaths[n−1] − 1` dans une même sauvegarde | Non |

Ce qui est **impossible** avec les données actuelles :

- **Le taux de réussite deathless** (tentatives réussies / tentatives) : il faut un événement de
  début de tentative et de complétion.
- **« Jusqu'où j'arrive »** exprimé en pourcentage de la map : il faut l'ordre des rooms.
- **Un taux de mort par room** (« je meurs 1 fois sur 4 ici ») : il faut un événement d'entrée dans
  la room.
- **Séparer les faces A / B / C** et **afficher le nom officiel** de la map.

---

## 3. Analyse : quelles données valent la peine d'être montrées

Critères : **intérêt pour un joueur de deathless**, **fiabilité** et **coût** (déjà disponible,
calcul à ajouter, ou changement du mod).

### 3.1 Le haut du panier

1. **Où mes tentatives s'arrêtent** (`room` des premières morts + `deathX/deathY`). C'est la
   réponse directe à « où mon deathless casse-t-il ? ». Une room qui concentre la moitié des échecs
   est la priorité d'entraînement ; la heatmap précise le passage.
2. **La durée et la progression des tentatives** (`timeInRoomMs`, puis ordre des rooms). « Ma
   meilleure tentative a tenu 6 min 40 », « je dépasse maintenant la room a-09 ». Montre si on
   progresse, ce qui motive.
3. **Le taux de réussite deathless par face** (nécessite les événements de début et de
   complétion). La mesure ultime : « 3 réussites sur 41 tentatives ».
4. **Le direct** (SSE) pendant une tentative : la room atteinte, le temps tenu, la mort qui met fin
   à l'essai.

### 3.2 Le milieu

- Morts par jour, par heure et par jour de la semaine : repérer quand on joue le mieux.
- Série de morts d'affilée dans une room : utile pour l'entraînement (le mode « practice »).
- Dernières morts, historique des tentatives.
- Morts manquées : indicateur de **santé de l'appairage** (page des appareils), pas une stat de
  joueur.

### 3.3 À retirer ou à nuancer

- **Le nombre total de morts** : retiré de l'interface (voir `totalDeaths`). Il disparaît du
  tableau de bord et du profil.
- **`timeInRoomMs` comme « temps dans la room »** : faux. À présenter comme « temps de la
  tentative ».
- **Classements entre joueurs** tant que l'horloge est celle du client : comparaisons falsifiables.
- **Les `SessionEvent` actuels** : inutilisables en l'état (non stockés, sémantique à vérifier).

### 3.4 Les ajouts au mod à faire avant la refonte

Décidés comme **prioritaires**, parce qu'ils conditionnent la structure même des pages :

| Donnée | Pourquoi | Source dans le jeu (à vérifier) | Effort |
|---|---|---|---|
| **Face de la map (A / B / C)** | Un deathless se joue sur une face précise ; sans elle toutes les stats sont mélangées | `level.Session.Area.Mode` | Faible : un champ |
| **Nom officiel de la map** (et du jeu de cartes) | Afficher « Forsaken City » plutôt que `Celeste/1-ForsakenCity` | Texte du dialogue du jeu (`Dialog.Clean(AreaData.Name)`), `AreaData.LevelSet` | Faible à moyen : à envoyer une fois par map et à garder dans un catalogue côté serveur |
| **Début de tentative et complétion** | Compter les tentatives **réussies** et le taux de réussite | Entrée dans la map et fin de chapitre (événements Everest) | Moyen : deux événements fiables |
| **Entrée dans une room** | « Jusqu'où j'arrive » et taux de mort par room | Hook sur la transition de room (`Level.TransitionTo`) | Moyen |
| **Identifiant de sauvegarde** | Séparer les profils ; fiabiliser la détection des morts manquées | Numéro de l'emplacement (`SaveData.FileSlot`) | Faible |
| **`registerStats`** | Le paramètre existe déjà dans le hook mais est ignoré : une « mort » qui ne compte pas pour le jeu est quand même envoyée | Argument `registerStats` de `Player.Die` | Faible : un test avant l'envoi |

Correction à faire en même temps : renommer `timeInRoomMs` en `sessionTimeMs` (nom honnête).

---

## 4. Évolutions futures (notées, pas pour la refonte)

### 4.1 Cause de la mort

Les stats deviennent bien plus parlantes avec la cause (pics, chute, ennemi, écrasement…) : « 60 %
de mes échecs sont des pics dans la room b-05 ».

- Source possible : l'argument `direction` de `Player.Die` et l'objet qui tue (à identifier côté
  mod). C'est le sujet le plus incertain : prévoir une phase d'investigation avant de promettre un
  résultat.
- Prérequis côté site : une colonne `cause` (nullable, pour les anciennes morts) et un filtre /
  graphique par cause.

### 4.2 Dashes utilisés (pour le less-dash)

Les défis « less-dash » (finir une map avec un nombre de dashes limité) demandent de compter les
dashes, comme le deathless demande de compter les morts.

- Source possible : `level.Session.Dashes` (compteur de la partie), à envoyer avec chaque mort et
  à la complétion. À vérifier : il s'agit bien du compteur de la partie en cours.
- Pour le site : nombre de dashes par tentative, par room, et record. Le modèle « tentative » de la
  section 0 s'y adapte tel quel.

### 4.3 Dessiner la map : les « bornes de la room »

*Explication simple.* Chaque room est un **rectangle** dans le repère de la map : un coin en haut à
gauche (`x`, `y`) et une taille (`largeur`, `hauteur`). Aujourd'hui on connaît seulement le point
exact de la mort, par exemple `(6 853, −2 221)`, mais **pas le rectangle** de la room qui le
contient. On ne sait donc pas si ce point est près du bord gauche ou du milieu, ni si la room est
grande ou petite.

Avec ce rectangle (quatre nombres par room, envoyés une fois par room) :

- on dessine le **cadre de la salle** et on place les morts dedans à la bonne échelle ;
- on peut afficher une heatmap lisible sur un fond reconnaissable ;
- on peut assembler les cadres de toutes les rooms pour obtenir une **vue d'ensemble de la map**
  (le plan des rooms, avec les zones où les tentatives s'arrêtent).

Source possible dans le jeu : `level.Bounds` (à vérifier).

**Attention aux droits.** Les nombres du rectangle ne posent aucun problème. En revanche, afficher
le **décor réel** (les tuiles, les pics, les graphismes) reviendrait à reproduire le contenu du jeu
d'Extremely OK Games et le travail des auteurs de maps. Il ne faut donc pas aller plus loin que des
cadres et des points (ou un schéma très simplifié) sans avoir vérifié l'autorisation. C'est la même
règle que pour les assets du jeu (voir `CLAUDE.md`).

---

## 5. Points de vigilance (récapitulatif)

1. Le site ne montre **pas** de total de morts : l'unité est la **tentative deathless par face**.
2. Identifier une room par **(`chapter`, face, `room`)**, jamais par le seul nom.
3. `timeInRoomMs` = temps de la partie, pas de room.
4. `totalDeaths` = compteur d'**une sauvegarde** ; usage interne uniquement.
5. `timestamp` vient de l'horloge du client.
6. Le SID ne distingue pas les faces A / B / C (à confirmer), et n'est pas un nom lisible.
7. Les `SessionEvent` ne sont pas conservés et leur sémantique est à vérifier.
8. Le mod envoie en fire-and-forget : des morts peuvent se perdre.
9. 35 morts historiques ont `user_id` NULL (import avant les comptes) : invisibles de tous.
10. Les données actuelles sont de l'**entraînement** (`sessionDeaths` jusqu'à 95), pas des
    tentatives deathless : elles ne suffisent pas à tester les écrans de deathless.

## 6. Pour la suite : refonte des pages

Décisions prises :

- Le site tourne autour de la **tentative deathless par face de map**.
- Le total de morts disparaît.
- Prioritaires côté mod : face, nom officiel, début de tentative / complétion, entrée de room,
  identifiant de sauvegarde, `registerStats`.
- Reportés : cause de la mort, dashes (less-dash), dessin de la map (bornes des rooms).

Prochaine étape : proposer l'arborescence des pages (par exemple : accueil, liste des maps avec nom
officiel, détail d'une face, détail d'une room, historique des tentatives, direct, appareils,
profil) et, pour chacune, les données affichées. Faire d'abord la mise à jour du mod, puis
l'arborescence, évite de la redessiner.
