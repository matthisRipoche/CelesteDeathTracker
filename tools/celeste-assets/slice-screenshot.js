// Découpe une capture PNG haute en bandes verticales. Usage : node slice.js in.png outPrefix bandHeight
const fs = require("fs");
const zlib = require("zlib");

const [inFile, prefix, bandArg] = process.argv.slice(2);
const buf = fs.readFileSync(inFile);

// --- décodage PNG (8 bits, RGB ou RGBA, non entrelacé) ---
let p = 8, w = 0, h = 0, ct = 0;
const idat = [];
while (p < buf.length) {
  const len = buf.readUInt32BE(p), type = buf.toString("ascii", p + 4, p + 8);
  const data = buf.subarray(p + 8, p + 8 + len);
  if (type === "IHDR") { w = data.readUInt32BE(0); h = data.readUInt32BE(4); ct = data[9]; }
  if (type === "IDAT") idat.push(data);
  p += 12 + len;
}
const bpp = ct === 6 ? 4 : 3;
const raw = zlib.inflateSync(Buffer.concat(idat));
const stride = w * bpp;
const px = Buffer.alloc(stride * h);
for (let y = 0; y < h; y++) {
  const f = raw[y * (stride + 1)];
  for (let x = 0; x < stride; x++) {
    const cur = raw[y * (stride + 1) + 1 + x];
    const a = x >= bpp ? px[y * stride + x - bpp] : 0;
    const b = y > 0 ? px[(y - 1) * stride + x] : 0;
    const c = x >= bpp && y > 0 ? px[(y - 1) * stride + x - bpp] : 0;
    let v;
    if (f === 0) v = cur;
    else if (f === 1) v = cur + a;
    else if (f === 2) v = cur + b;
    else if (f === 3) v = cur + ((a + b) >> 1);
    else { const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c); v = cur + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c); }
    px[y * stride + x] = v & 255;
  }
}

// --- ré-encodage ---
const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
function encode(rows, hh) {
  const out = Buffer.alloc((stride + 1) * hh);
  for (let y = 0; y < hh; y++) { out[y * (stride + 1)] = 0; rows.copy(out, y * (stride + 1) + 1, y * stride, (y + 1) * stride); }
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(hh, 4); ih[8] = 8; ih[9] = ct;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(out)), chunk("IEND", Buffer.alloc(0))]);
}

const band = Number(bandArg || 1300);
let n = 0;
for (let y = 0; y < h; y += band) {
  const hh = Math.min(band, h - y);
  fs.writeFileSync(`${prefix}-${++n}.png`, encode(px.subarray(y * stride, (y + hh) * stride), hh));
}
console.log(`${w}x${h} -> ${n} bande(s)`);
