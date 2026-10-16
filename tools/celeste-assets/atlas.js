// Lecture des atlas de Celeste (.meta + .data) -> inventaire des sprites + export PNG.
// Usage : node atlas.js <Atlas> [--list] [--sheet] [--crop <regex>] [--out <dossier>] [--swap]
//   <Atlas> : Gui | Gameplay | Journal | FileSelect | Memo ...
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const DIR = (process.env.CELESTE_PATH || "D:/steam/steamapps/common/Celeste") + "/Content/Graphics/Atlases";
const args = process.argv.slice(2);
const name = args[0];
const flag = (f) => args.includes(f);
const opt = (f) => (args.includes(f) ? args[args.indexOf(f) + 1] : null);

// ---------- .meta : binaire, chaînes .NET (longueur en 7 bits) ----------
class Reader {
  constructor(buf) { this.b = buf; this.p = 0; }
  i32() { const v = this.b.readInt32LE(this.p); this.p += 4; return v; }
  i16() { const v = this.b.readInt16LE(this.p); this.p += 2; return v; }
  u8() { return this.b[this.p++]; }
  str() {
    let len = 0, shift = 0, byte;
    do { byte = this.u8(); len |= (byte & 0x7f) << shift; shift += 7; } while (byte & 0x80);
    const s = this.b.toString("utf8", this.p, this.p + len);
    this.p += len;
    return s;
  }
}

function readMeta(file) {
  const r = new Reader(fs.readFileSync(file));
  r.i32();            // version
  r.str();            // "CRUNCH" ou similaire
  r.i32();
  const textures = [];
  const nTex = r.i16();
  for (let t = 0; t < nTex; t++) {
    const dataName = r.str();
    const nSub = r.i16();
    const subs = [];
    for (let s = 0; s < nSub; s++) {
      const p = r.str().replace(/\\/g, "/");
      const x = r.i16(), y = r.i16(), w = r.i16(), h = r.i16();
      const ox = r.i16(), oy = r.i16(), rw = r.i16(), rh = r.i16();
      subs.push({ path: p, x, y, w, h, ox, oy, rw, rh });
    }
    textures.push({ dataName, subs });
  }
  return textures;
}

// ---------- .data : en-tête (largeur, hauteur, alpha) puis pixels compressés par répétition ----------
function readData(file, swap) {
  const buf = fs.readFileSync(file);
  const w = buf.readInt32LE(0), h = buf.readInt32LE(4), hasAlpha = buf[8] === 1;
  const out = Buffer.alloc(w * h * 4);
  let p = 9, n = 0;
  while (n < out.length) {
    const run = buf[p++];
    let a = 255, c0, c1, c2;
    if (hasAlpha) {
      a = buf[p++];
      if (a > 0) { c0 = buf[p++]; c1 = buf[p++]; c2 = buf[p++]; } else { c0 = c1 = c2 = 0; }
    } else { c0 = buf[p++]; c1 = buf[p++]; c2 = buf[p++]; }
    // Les canaux sont stockés B,G,R (premultipliés). On les remet en RGB.
    const [R, G, B] = swap ? [c0, c1, c2] : [c2, c1, c0];
    for (let i = 0; i < run && n < out.length; i++) {
      out[n++] = R; out[n++] = G; out[n++] = B; out[n++] = a;
    }
  }
  return { w, h, rgba: out, hasAlpha };
}

// ---------- PNG minimal ----------
const crcTable = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; }
  return t;
})();
const crc32 = (buf) => { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}
function crop(img, s) {
  const out = Buffer.alloc(s.w * s.h * 4);
  for (let y = 0; y < s.h; y++) img.rgba.copy(out, y * s.w * 4, ((s.y + y) * img.w + s.x) * 4, ((s.y + y) * img.w + s.x + s.w) * 4);
  return out;
}

// ---------- programme ----------
if (!name) { console.log("Atlas manquant"); process.exit(1); }
const metaFile = path.join(DIR, name + ".meta");
const textures = readMeta(metaFile);
const all = textures.flatMap((t) => t.subs.map((s) => ({ ...s, data: t.dataName })));

if (flag("--list") || !opt("--crop")) {
  const byFolder = {};
  for (const s of all) { const f = s.path.split("/").slice(0, -1).join("/") || "(racine)"; (byFolder[f] ||= []).push(s); }
  console.log(`${name} : ${textures.length} texture(s), ${all.length} sprites`);
  for (const [f, list] of Object.entries(byFolder).sort((a, b) => b[1].length - a[1].length).slice(0, Number(opt("--top") || 40)))
    console.log(String(list.length).padStart(5), f);
}

const swap = flag("--swap");
if (flag("--sheet") || opt("--crop")) {
  const outDir = opt("--out") || ".";
  fs.mkdirSync(outDir, { recursive: true });
  const cache = {};
  const img = (dn) => (cache[dn] ||= readData(path.join(DIR, dn + ".data"), swap));
  if (flag("--sheet")) {
    const tex = textures[0];
    const im = img(tex.dataName);
    fs.writeFileSync(path.join(outDir, `${name}-sheet.png`), png(im.w, im.h, im.rgba));
    console.log(`feuille : ${im.w}x${im.h} -> ${name}-sheet.png`);
  }
  if (opt("--crop")) {
    const re = new RegExp(opt("--crop"), "i");
    let count = 0;
    for (const s of all.filter((x) => re.test(x.path))) {
      const im = img(s.data);
      const file = path.join(outDir, s.path.replace(/\//g, "__") + ".png");
      fs.writeFileSync(file, png(s.w, s.h, crop(im, s)));
      count++;
    }
    console.log(`${count} sprite(s) exporté(s) dans ${outDir}`);
  }
}
