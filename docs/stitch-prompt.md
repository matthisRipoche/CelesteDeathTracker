# Prompt Stitch — maquette de Celeste Death Tracker

Ce document sert à générer, dans Google Stitch, une maquette cohérente du site et de son design
system, réutilisable ensuite pour l'implémentation Angular (les valeurs ci-dessous sont celles de
`web/src/styles/tokens.css`).

## Mode d'emploi

1. Colle d'abord le **prompt maître** (§1) : il fixe le design system et le contexte.
2. Génère ensuite **un écran à la fois** avec les prompts du §2. Un écran par prompt donne de
   meilleurs résultats qu'un seul énorme prompt.
3. Pour chaque écran, rappelle en début de prompt : « Same design system as before. »
4. Garde la **version bureau** et la **version mobile** (360 px) des écrans principaux.
5. Exporte (Figma ou code) et garde les captures dans `docs/maquettes/`.

> Le prompt est en anglais (meilleur résultat dans l'outil) ; les textes de l'interface sont
> imposés **en français** et figurent entre guillemets.

---

## 1. Prompt maître (à coller en premier)

```
Design a responsive web app called "Celeste Death Tracker": a personal dashboard where a Celeste
(the platformer game) player sees where and how often they die. Players link their game with a
short code; the site then shows statistics, rankings and heatmaps of death positions.

LANGUAGE: all interface text in French. Keep it concise and friendly.

AUDIENCE: gamers, 15–35, using desktop (1280–1440 px wide) and phone (360–390 px). Data-dense but
calm. Think "game stats site", not a corporate dashboard.

VISUAL STYLE — design system "Basecamp":
- Dark theme only. Mood: night mountain, lavender and ice blue, with hot pink as the single
  accent. Crisp, slightly retro pixel-inspired, never childish. Flat colours, no photos, no
  gradients on surfaces.
- Do NOT use any artwork, characters, logo or sprites from the Celeste game. Brand mark = a simple
  geometric mountain peak icon (lavender triangle with a small ice-blue snow cap) + the wordmark
  "Death Tracker".

COLOURS (use exactly these):
  Backgrounds:   page #0E0C1D, surface/card #151229, raised surface #1D1938, sunken input #0A0916
  Borders:       #3B3470 (default), #675C83 (strong)
  Text:          primary #F5F2FF, secondary #9D97D1, faint #675C83
  Accent (CTA, active nav, data bars): pink #F8588C  (hover #F73E7B, text on accent #0E0C1D)
  Info / links / focus: ice blue #66C4E1 (light #A6DCEE)
  Success: green #93BD40    Warning / pairing code: gold #F2C230    Danger / death counts: red #ED4A44
  Soft accent fill: pink at 14% opacity

TYPOGRAPHY:
  - "Outfit" for all text and headings (weights 400/600/700).
  - "Pixelify Sans" ONLY for big numbers (>= 24 px), the pairing code and the wordmark.
    Never use the pixel font for small text or table cells.
  Scale: 12 / 14 / 16 / 20 / 26 / 36 / 52 px. Body 16 px, line-height 1.5. Minimum text 12 px.

SHAPE & SPACING:
  - 4 px spacing grid (4, 8, 12, 16, 24, 32, 48, 64).
  - Corner radius 2 / 4 / 8 px (buttons and inputs 4, cards 8). Borders 2 px.
  - Hard drop shadow, no blur: 0 3px 0 rgba(0,0,0,0.45). Buttons "press down" on click.
  - Content max width 1120 px, centred. Interactive targets >= 44 px high.

COMPONENTS:
  Buttons: primary (pink fill), secondary (raised surface + strong border), ghost, danger
  (red outline), sizes sm 34 / md 44 / lg 54 px, loading and disabled states.
  Inputs: sunken background, 2 px border, ice-blue focus ring, error state in red with message.
  Code input: large pixel font, letter-spaced, uppercase, placeholder "XXXX-XXXX".
  Cards with small uppercase section title (12 px, letter-spaced, secondary colour).
  Stat tile: label (12 px caps) + big pixel number (52 px) + small delta line.
  Badges: small uppercase, 2 px outline, coloured by meaning.
  Alerts: info / success / warning / danger, tinted background + coloured 2 px border + icon.
  Table: sticky uppercase header, hover row tint, numbers right-aligned.
  Ranking bars: label, striped 12 px bar in pink (or ice blue), value on the right.
  Tabs (segmented), empty state (dashed border), skeleton loader, toast, modal dialog.

NAVIGATION:
  Desktop: sticky top bar 60 px: brand left; links "Tableau de bord", "Maps", "Sessions",
  "Appareils"; user menu on the right (email, "Compte", "Déconnexion"). Active link has a 3 px pink
  underline. Mobile: top bar with brand only + a fixed bottom tab bar with 4 icon+label items
  (Accueil, Maps, Sessions, Appareils); no hamburger menu.
  A filter bar under the title on data pages: period segmented control ("Aujourd'hui",
  "7 jours", "30 jours", "Tout") and a map selector.

REALISTIC SAMPLE DATA (use throughout, consistent across screens):
  Player: matthis@exemple.fr. Total deaths 35 507; today 128; current streak 17 deaths in the same
  room; deadliest room "Summit / b-05" with 42 deaths.
  Maps (mod / map): "Celeste / 1-ForsakenCity", "monikadsidespack / 7-Summit",
  "darkmoonruins / 1-moonruins", "MoonRuins / 1-moonruins".
  Rooms: b-05, b-06, a-09, a-11, c-03. Coordinates are pixels, e.g. X 7002, Y -2168.
  Devices: "PC-DE-MATTHIS" (Production, last used 2 min ago), "Portable" (last used 3 days ago).
  Pairing code example: K7QM-4XRD.

ACCESSIBILITY: WCAG AA contrast, visible focus, never rely on colour alone, labels on all inputs.

Confirm you have the design system, then wait for the screens.
```

---

## 2. Prompts par écran

### 2.1 Page d'accueil (visiteur non connecté)
```
Same design system as before. Screen: public landing page (desktop 1440 + mobile 390).
Top bar: brand + buttons "Se connecter" (ghost) and "Créer un compte" (primary).
Hero: title "Sais-tu où tu meurs le plus ?", subtitle "Associe ton jeu Celeste et vois tes morts
s'accumuler, room par room." Primary CTA "Créer un compte", secondary "Se connecter".
Next to the hero, an illustrative dashboard preview card (stat tile 35 507 + three ranking bars),
abstract, no game art.
Section "Comment ça marche" with 3 numbered steps in cards:
 1 "Installe le mod" — "Ajoute Death Tracker à Everest."
 2 "Saisis le code" — "Le jeu affiche un code, tu le tapes sur le site."
 3 "Meurs une fois" — "Tes statistiques apparaissent en direct."
Footer: small text "Projet de fans, non affilié à Extremely OK Games." 
```

### 2.2 Connexion / inscription
```
Same design system. Screen: authentication, centred card 400 px on the page background.
Segmented control "Connexion | Inscription". Fields: "Email", "Mot de passe" (hint
"8 caractères minimum" on sign-up). Primary full-width button "Se connecter" / "Créer le compte".
Show the error state: red message "Email ou mot de passe incorrect." Small link
"Mot de passe oublié ?" (disabled, coming soon). Desktop and mobile.
```

### 2.3 Tableau de bord (avec données)
```
Same design system. Screen: dashboard "Tableau de bord", desktop 1440 + mobile 390.
Filter bar: periods + map selector.
Row of 4 stat tiles: "Morts totales" 35 507 (red number), "Aujourd'hui" 128 with "+128 vs hier",
"Série en cours" 17 "morts dans la même room", "Room la plus meurtrière" "Summit / b-05" (42).
Card "Morts par jour" : bar chart for the last 30 days, pink bars, y-axis light, today highlighted.
Two columns: card "Rooms les plus meurtrières" (6 ranking bars, each a link, with a chevron) and
card "En direct" (live feed of the last 6 deaths: time, map, room, small "il y a 3 s" label, a
pulsing green dot "en direct" at the top).
Mobile: tiles in 2x2 grid, cards stacked, bottom tab bar visible.
```

### 2.4 Tableau de bord — première visite (aucune donnée)
```
Same design system. Screen: dashboard first-run state. No statistics yet. Replace the content
by a centred onboarding card "Bienvenue !" with a vertical stepper of 3 steps:
 1 "Lance Celeste avec le mod Death Tracker" (done check if applicable)
 2 "Saisis le code affiché dans le jeu" — button "Associer un jeu"
 3 "Meurs une fois… ça arrive vite" — waiting state with a subtle animated dot.
Below, a dashed empty state preview of the stat tiles with "—" values.
```

### 2.5 Mes maps
```
Same design system. Screen: "Mes maps". Filter bar: period + sort ("Plus de morts", "Plus
récentes", "A à Z"). Grid of map cards (3 columns desktop, 1 mobile). Each card: map name in bold,
mod name in secondary text, a big pixel number of deaths, "214 rooms touchées", "dernière partie
il y a 2 h", and a mini sparkline of deaths per day. Include 6 cards using the sample maps.
Include a search field "Rechercher une map".
```

### 2.6 Détail d'une map
```
Same design system. Screen: map detail "monikadsidespack / 7-Summit". Breadcrumb "Maps › 7-Summit".
Header: map name, mod badge, stat tiles: morts 4 812, rooms 38, "temps passé" 6 h 12.
Card "Évolution" line/area chart of deaths per day (pink line, soft fill).
Card "Rooms de la plus meurtrière à la moins meurtrière": table with columns Room, Morts,
Part (%), Temps moyen, mini bar; rows clickable; first row highlighted. Pagination.
```

### 2.7 Détail d'une room — carte de chaleur
```
Same design system. Screen: room detail "Summit / b-05". Breadcrumb "Maps › 7-Summit › b-05".
Stat tiles: "Morts" 42, "Par tentative" 3,2, "Temps moyen" "48 s", "Dernière mort" "il y a 3 min".
Main card "Où tu meurs": a heatmap on a dark 16:9 canvas with a faint pixel grid and axis ticks in
pixels (X from 6800 to 7100, Y from -2300 to -2100). Death points as glowing dots: pink for low
density, orange then gold for high density. A legend "moins → plus de morts". Right side panel:
"Zones critiques" list of 3 clusters with count and coordinates, hovering highlights the cluster.
Below: card "Chronologie" listing the last deaths with time and coordinates.
No game art or level screenshots.
```

### 2.8 Sessions
```
Same design system. Screen: "Sessions". Stat tiles: "Sessions" 24, "Durée moyenne" "1 h 12",
"Morts / heure" 86, "Meilleure session" "37 morts / h". Card "Mes sessions": list of sessions,
each row: date and start time, map, duration, deaths, deaths per hour as a small bar, badge
"Record" on the best one. Row click expands to a small timeline of deaths during the session.
```

### 2.9 Appareils et association
```
Same design system. Screen: "Appareils". Primary button "Associer un jeu" top right.
Card list of devices: name "PC-DE-MATTHIS", badge "Production" (ice blue), "associé le 15/10",
"dernier envoi il y a 2 min", status dot green, button "Révoquer" (danger). Second device
"Portable" with "dernier envoi il y a 3 jours" and a "Local" badge in neutral.
Also design the modal flow "Associer un jeu" in three states, side by side:
 (a) "Saisis le code affiché dans ton jeu" with the large code input and "Continuer";
 (b) confirmation: "Autoriser cet appareil ?" with device name PC-DE-MATTHIS, requested time, a
     gold warning "Autorise seulement si c'est toi qui viens de lancer le jeu." and buttons
     "Autoriser" (primary) and "Refuser" (secondary);
 (c) success: green check "Jeu associé", "Retourne dans Celeste, tes morts arrivent en direct."
Also an error state for an expired code.
```

### 2.10 Compte
```
Same design system. Screen: "Compte". Sections as cards: "Profil" (email, creation date),
"Mot de passe" (current, new, confirm, button "Mettre à jour"), "Mes données" (button
"Exporter mes données (JSON)"), and a red-bordered "Zone dangereuse" with "Supprimer mon compte"
and a confirmation dialog that requires typing "SUPPRIMER".
```

### 2.11 Planche du design system
```
Same design system. Screen: a "Design system" reference sheet, desktop 1440. Sections: colour
palette with names and hex values, typography scale, spacing scale, radii and shadows, buttons
(all variants and states), form fields (default, focus, error, disabled, code input), cards,
stat tile, badges, alerts, table, ranking bars, tabs, navigation bar (desktop) and bottom tab
bar (mobile), empty state, skeleton, toast, modal. Label every component.
```

---

## 3. Après la génération

Ce que je fais des maquettes :
1. **Comparer** les valeurs de la planche Stitch aux jetons de `web/src/styles/tokens.css` et
   ajuster ceux qui divergent (une ligne par jeton).
2. **Implémenter la V1** (coque de navigation, tableau de bord, Appareils, première visite,
   accueil) en réutilisant les classes `.ds-*`.
3. Garder les maquettes comme **référence** pour les pages V2/V3 (Maps, Room, Sessions, Compte).

Rappel : les pages Maps, Room, Sessions et Compte demandent du travail côté API (statistiques,
persistance des sessions). La maquette peut les décrire dès maintenant ; elles seront
implémentées par étapes.

---

## 4. Connexion de Claude Code à Stitch (MCP)

Le fichier `.mcp.json` à la racine déclare le serveur MCP de Stitch. **Il ne contient aucun secret** :
la clé est lue dans la variable d'environnement `STITCH_API_KEY`.

1. Dans Stitch : *Paramètres → Clé d'API* → créer une clé.
2. La définir sur ta machine (PowerShell, une seule fois) :
   ```powershell
   [Environment]::SetEnvironmentVariable("STITCH_API_KEY", "<ta clé>", "User")
   ```
3. **Redémarrer Claude Code** (les serveurs MCP ne se chargent qu'au démarrage), accepter le serveur du
   projet quand il le propose, puis vérifier avec `/mcp` : `stitch` doit être « Connected ».
4. Ne jamais coller la clé dans un fichier du dépôt ni dans une conversation.

Si Claude Code échoue avec « Incompatible auth server: does not support dynamic client registration »,
c'est un défaut connu : il tente une découverte OAuth au lieu d'utiliser l'en-tête. Alternative :
passer par un pont local (`@_davideast/stitch-mcp`, vérifier sa commande avec `npx @_davideast/stitch-mcp doctor`).
