// Exporte une sélection de sprites (usage LOCAL uniquement) et calcule des palettes depuis les sprites réels.
const fs = require("fs");
const path = require("path");
const src = fs.readFileSync(path.join(__dirname, "atlas.js"), "utf8").split("// ---------- programme ----------")[0];
const m = {};
new Function("require", "module", "process", src + "\nmodule.exports={readMeta,readData,png,crop};")(require, m, process);
const { readMeta, readData, png, crop } = m.exports;

const DIR = (process.env.CELESTE_PATH || "D:/steam/steamapps/common/Celeste") + "/Content/Graphics/Atlases";
const OUT = path.join(__dirname, "../../web/public/local-assets/celeste");
fs.mkdirSync(OUT, { recursive: true });

// Sélection : [atlas, nom du sprite dans l'atlas, nom de fichier exporté]
const WANT = {
  Gui: [
    ["logo", "logo"], ["title", "title"],
    ["collectables/strawberry", "strawberry"], ["collectables/goldberry", "goldberry"],
    ["collectables/dashes", "dashes"], ["strawberryCountBG", "strawberry-count-bg"],
    ["menu/play", "menu-play"], ["menu/options", "menu-options"], ["menu/journal", "menu-journal"],
    ["menu/exit", "menu-exit"], ["menu/start", "menu-start"],
    ["areas/intro", "area-intro"], ["areas/city", "area-city"], ["areas/oldsite", "area-oldsite"],
    ["areas/resort", "area-resort"], ["areas/cliffside", "area-cliffside"],
    ["areas/temple", "area-temple"], ["areas/reflection", "area-reflection"],
    ["areas/core", "area-core"], ["areas/farewell", "area-farewell"],
    ["areaselect/startpoint", "startpoint"], ["areaselect/checkpoint", "checkpoint"],
    ["downarrow", "arrow-down"], ["textboxbutton", "textbox-button"],
  ],
  Gameplay: [
    ["characters/player/idle00", "madeline-idle"],
    ["characters/player/dash01", "madeline-dash"],
    ["characters/player/jumpFast02", "madeline-jump"],
    ["characters/player/death_h03", "madeline-death-1"],
    ["characters/player/death_h09", "madeline-death-2"],
    ["characters/player_badeline/idle00", "badeline-idle"],
    ["collectables/strawberry/normal00", "strawberry-pixel"],
    ["objects/refill/idle00", "dash-crystal"],
  ],
};

function palette(rgba, top = 6) {
  const hist = new Map();
  for (let i = 0; i < rgba.length; i += 4) {
    if (rgba[i + 3] < 200) continue;
    const r = rgba[i] >> 4, g = rgba[i + 1] >> 4, b = rgba[i + 2] >> 4;
    const lum = 0.2126 * rgba[i] + 0.7152 * rgba[i + 1] + 0.0722 * rgba[i + 2];
    if (lum < 25) continue; // contours noirs
    const k = (r << 8) | (g << 4) | b;
    const e = hist.get(k) || { n: 0, r: 0, g: 0, b: 0 };
    e.n++; e.r += rgba[i]; e.g += rgba[i + 1]; e.b += rgba[i + 2];
    hist.set(k, e);
  }
  return [...hist.values()].sort((a, b) => b.n - a.n).slice(0, top)
    .map((e) => "#" + [e.r, e.g, e.b].map((v) => Math.round(v / e.n).toString(16).padStart(2, "0")).join(""));
}

const report = {};
const index = [];
for (const [atlas, list] of Object.entries(WANT)) {
  const subs = readMeta(path.join(DIR, atlas + ".meta"));
  const tex = subs[0];
  const sheet = readData(path.join(DIR, tex.dataName + ".data"), false);
  for (const [spritePath, file] of list) {
    const s = tex.subs.find((x) => x.path === spritePath);
    if (!s) { console.log("introuvable :", atlas, spritePath); continue; }
    const px = crop(sheet, s);
    fs.writeFileSync(path.join(OUT, file + ".png"), png(s.w, s.h, px));
    report[file] = palette(px);
    index.push({ file: file + ".png", from: `${atlas}:${spritePath}`, w: s.w, h: s.h });
  }
}
fs.writeFileSync(path.join(OUT, "index.json"), JSON.stringify(index, null, 2));
console.log(`${index.length} sprites exportés dans ${OUT}\n`);
for (const k of ["madeline-idle", "madeline-dash", "badeline-idle", "strawberry", "goldberry", "dashes", "dash-crystal", "logo", "area-city", "area-resort", "area-temple", "area-core", "menu-play"])
  if (report[k]) console.log(k.padEnd(16), report[k].join(" "));
