# Extraction des assets de Celeste (usage local)

Outils pour lire les atlas du jeu (`Content/Graphics/Atlases/*.meta` + `*.data`, format maison) et en
exporter des sprites en PNG. **Ne contient aucun asset du jeu.**

> Les sprites sont © Extremely OK Games. Ils servent à s'inspirer et à alimenter la vitrine locale du
> design system. Ils sont exclus de Git (`web/public/local-assets/`), de l'image Docker
> (`web/.dockerignore`) et du build de production (`angular.json`). Ne les publie pas.

```bash
# inventaire d'un atlas (Gui, Gameplay, Journal, FileSelect, Memo...)
node tools/celeste-assets/atlas.js Gui --list

# planche entière, ou sprites dont le chemin correspond à une expression régulière
node tools/celeste-assets/atlas.js Gui --sheet --out ./sortie
node tools/celeste-assets/atlas.js Gameplay --crop "^characters/player/idle" --out ./sortie

# sélection utilisée par la vitrine du design system -> web/public/local-assets/celeste/
node tools/celeste-assets/export.js
```

`CELESTE_PATH` change le dossier du jeu (défaut : `D:/steam/steamapps/common/Celeste`).
`slice-screenshot.js` découpe une capture haute en bandes (utile pour relire la vitrine en headless).
