# Design system « Basecamp »

Système de design du site, posé sur la palette du jeu. Code : `web/src/styles/` ; vitrine :
`web/src/app/features/design-system/`.

## Voir la vitrine

```bash
cd web && npm start        # puis http://localhost:4200/design-system
```
La page n'existe **qu'en développement** : en production, `dev-routes.ts` est remplacé par
`dev-routes.prod.ts` (liste vide) à la compilation (`angular.json`, `fileReplacements`), et le dossier
`public/local-assets/` est exclu des assets. Vérifié : ni la page ni les sprites n'entrent dans `dist/`.

## Principes
1. **Lisible avant tout** : contrastes AA, 12 px minimum, focus toujours visible.
2. **Pixel avec parcimonie** : la police pixel est réservée aux grands chiffres (≥ 24 px), aux codes et à la
   marque. Elle devient illisible en petit (testé).
3. **Franc et tactile** : angles nets, ombres dures sans flou, boutons qui s'enfoncent.

## Jetons (`tokens.css`)
- **Palette brute** `--ds-*` : échantillonnée sur les sprites du jeu (logo lavande/givre `#9d97d1`/`#66c4e1`,
  fraise `#ed4a44`, dash `#f8588c`, cristal `#93bd40`). Ne s'utilise jamais dans un composant.
- **Sémantique** `--color-*` : `bg`, `surface`, `border`, `text`, `accent`, `info`, `success`, `warning`,
  `danger`. C'est ce que les composants consomment ; un autre thème = redéfinir ces jetons.
- Typographie, espacement (base 4 px), angles, ombres, durées (`prefers-reduced-motion` respecté).

## Typographie
| Rôle | Police | Licence |
|---|---|---|
| Texte, titres | **Outfit** (variable) | OFL-1.1 |
| Chiffres, codes, marque | **Pixelify Sans** (variable) | OFL-1.1 |

Hébergées avec le site (`@fontsource-variable/*`) : aucune requête vers un service tiers.
La police du jeu, **Renogare** (confirmée par `Content/Dialog/Fonts/renogare*.fnt`), a une licence
« custom » : non redistribuable ici, d'où Outfit, de caractère proche.

## Composants (`ds.css`)
Bouton (primaire, secondaire, discret, danger, tailles, chargement), champ et saisie de code, carte,
indicateur chiffré, badge, alerte, tableau, barre de classement, barre de navigation (haut + barre du bas
mobile), onglets, état vide, squelette de chargement, code d'association, toast.

## Navigation cible
Barre en haut sur ordinateur (marque · Tableau de bord · Appareils · Associer un jeu · compte),
remplacée par une barre du bas sur mobile. Trois destinations : pas de menu hamburger.

## Assets du jeu : ce qu'il y a
Voir `tools/celeste-assets/README.md`. Atlas `Gui` (422 sprites : cœurs, fraises, icônes de chapitres,
logo, touches de manette/clavier, papiers), `Gameplay` (6 824 sprites : Madeline, Badeline, Theo, objets,
décors), `Journal`, `FileSelect`, `Memo`. Aucune politique de contenu de fans publiée par le studio n'a
été trouvée : considérer ces assets comme protégés.
