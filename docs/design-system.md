# Design system « Topo »

Système de design du site : un carnet de cordée pour suivre ses tentatives de deathless. Papier glacier
ou ardoise de nuit, encre de roche, et un seul surligneur, la fraise dorée. Code : `web/src/styles/` ;
vitrine : `web/src/app/features/design-system/`.

Maquettes de référence : canvas Claude Design « Death Tracker, maquettes » (planche du design system,
détail d'une face et tableau de bord, en clair et en sombre). Le lien est privé : le demander au
propriétaire du projet.

## Voir la vitrine

```bash
cd web && npm start        # puis http://localhost:4200/design-system
```
La page n'existe **qu'en développement** : en production, `dev-routes.ts` est remplacé par
`dev-routes.prod.ts` (liste vide) à la compilation (`angular.json`, `fileReplacements`), et le dossier
`public/local-assets/` est exclu des assets. Vérifié : ni la page ni les sprites n'entrent dans `dist/`.

## Principes
1. **Lisible avant tout** : contrastes d'au moins 4,5:1 dans les deux thèmes, texte de 13 px minimum,
   cibles de 44 px, focus toujours visible (double anneau encre puis surligneur).
2. **Des filets, pas des boîtes** : pas de cartes ni d'ombres. Un trait épais ouvre chaque section, le titre
   porte l'information. Les chiffres sont étroits et très gras.
3. **Un seul surligneur doré par écran** : il marque l'élément à regarder (la room fatale, le direct).
   Jamais de décoration.
4. **Les couleurs ont un sens** : vert = sommet (réussite), rouge = chute (échec). Un résultat se lit aussi
   par une forme et un mot, jamais par la couleur seule.
5. **Celeste par ses objets, pas par son décor** : la fraise dorée marque un deathless réussi, l'explosion
   de pixels une mort, un glyphe pixel par chapitre. Ces motifs sont dessinés pour ce site.
6. **Rédaction** : phrases en minuscules, voix active, un bouton dit ce qu'il fait. Ni majuscules espacées,
   ni flèche collée aux liens, ni point médian entre les mots.

## Jetons (`tokens.css`)
- **Palette brute** `--ds-*` : papier `#eef1ee`, encre `#1b2a2e`, granit `#53646a`, trait `#c5cfcb`, sommet
  `#1f7a4d`, chute `#b5382a`, fraise dorée `#f2d31b` ; ardoise `#0f181b`, neige `#e7edea`, brume `#9db0ac`,
  sommet clair `#4fbf86`, chute claire `#f0705f`. Ne s'utilise jamais dans un composant.
- **Sémantique** `--color-*` : `bg`, `surface`, `surface-raised`, `border`, `border-strong`, `text`,
  `text-muted`, `accent`, `highlight`, `success`, `danger`, `chrome` (barre du haut). C'est ce que les
  composants consomment.
- **Thèmes** : le clair est le défaut. Le sombre suit `prefers-color-scheme: dark` ; on le force avec
  `data-theme="dark"` (ou `"light"`) sur `<html>`. Clair et sombre redéfinissent **uniquement** les jetons
  sémantiques : aucun composant n'a de couleur propre.
- Typographie, espacement (base 4 px), angles (3 px), durées (`prefers-reduced-motion` respecté).

## Typographie
| Rôle | Police | Licence |
|---|---|---|
| Texte, titres, chiffres | **Archivo** (variable, axe de largeur) | OFL-1.1 |

Deux largeurs d'une même famille : normale pour le texte, étroite (`font-stretch: 62 %`) et graisse 800
pour les chiffres et titres d'affichage (`.ds-display`, `.ds-stat__value`). Hébergée avec le site
(`@fontsource-variable/archivo/wdth.css`) : aucune requête vers un service tiers.
La police du jeu, **Renogare**, a une licence « custom » : non redistribuable ici.

## Composants (`ds.css`)
Bouton (primaire, secondaire, discret, danger, tailles, chargement), champ et saisie de code, section
(`.ds-card`, sans boîte), indicateur chiffré, badge, face de map (`.ds-face`), alerte, tableau, barre de
classement, barre du haut et barre du bas mobile, onglets, état vide, squelette de chargement, code
d'association, toast, surligneur (`.ds-mark`) et motifs pixel (`.ds-px-*`).

## Motifs de Celeste
Dessinés pour ce site en SVG pixel (voir les maquettes) :
- **Fraise dorée** : un deathless réussi (dans le jeu, elle se gagne en finissant un chapitre sans mourir).
- **Explosion de pixels** (huit carrés en couronne) : une tentative qui se termine par une mort.
- **Glyphe de chapitre** : une silhouette pixel par map.
- **Silhouette de montagne** : décor de la barre du haut.
- **Cristal de dash** : réservé au futur suivi des dashes.

## Navigation cible
Barre latérale à gauche sur ordinateur (`.ds-sidebar` : marque, Tableau de bord · Maps · Historique · Direct,
et en bas le compte avec sa photo de profil `app-avatar` et le menu `.ds-menu`), remplacée par une barre du
bas sur mobile, plus un bandeau doré quand une tentative est en cours. Le contenu prend toute la largeur
(plus de `--content-width` dans la coque ; `--sidebar-width` règle la barre). Voir
`docs/arborescence-pages.md`.

## Assets du jeu
Voir `tools/celeste-assets/README.md`. Les sprites sont © Extremely OK Games, aucune politique de
contenu de fans n'a été trouvée : ils restent **locaux** (`public/local-assets/`, hors Git, hors Docker,
hors production) et ne sont jamais publiés, y compris dans des maquettes hébergées. Le composant
`app-sprite` les affiche en développement et disparaît s'ils sont absents.
