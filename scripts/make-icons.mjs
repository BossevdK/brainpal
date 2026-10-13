// Tekent het BrainPal-icoon (wit brein op paars) als PNG in drie maten. Gebruik: node scripts/make-icons.mjs
import { deflateSync } from "node:zlib";
import { writeFileSync, mkdirSync } from "node:fs";

const BG = [0x5b, 0x4b, 0xdb], WHITE = [255, 255, 255];

// Brein op een canvas van 512x512: twee helften, elk een samenstelling van cirkels, met plooien als lijnen
const lobes = [[205, 215, 72], [165, 268, 62], [202, 318, 58], [238, 266, 76], [236, 205, 58]];
const left = lobes.map(([x, y, r]) => ({ x, y, r }));
const right = lobes.map(([x, y, r]) => ({ x: 512 - x, y, r }));
const folds = [
  [[172, 236], [205, 250], [238, 232]], [[168, 292], [205, 284], [236, 300]], [[205, 180], [215, 205], [200, 228]],
].flatMap((p) => [p, p.map(([x, y]) => [512 - x, y])]);

function distSeg(px, py, [ax, ay], [bx, by]) {
  const dx = bx - ax, dy = by - ay;
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}
function colorAt(x, y) {
  const inBrain = [...left, ...right].some((c) => Math.hypot(x - c.x, y - c.y) <= c.r);
  if (!inBrain) return BG;
  if (Math.abs(x - 256) < 5) return BG; // scheiding tussen de helften
  for (const f of folds) for (let i = 0; i < f.length - 1; i++) if (distSeg(x, y, f[i], f[i + 1]) < 4) return BG;
  return WHITE;
}

const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = (buf) => { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(size) {
  const SS = 3; // 3x3 punten per pixel voor gladde randen
  const raw = Buffer.alloc(size * (size * 3 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 3 + 1)] = 0;
    for (let x = 0; x < size; x++) {
      let r = 0, g = 0, b = 0;
      for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
        const c = colorAt(((x + (sx + 0.5) / SS) / size) * 512, ((y + (sy + 0.5) / SS) / size) * 512);
        r += c[0]; g += c[1]; b += c[2];
      }
      const o = y * (size * 3 + 1) + 1 + x * 3, n = SS * SS;
      raw[o] = Math.round(r / n); raw[o + 1] = Math.round(g / n); raw[o + 2] = Math.round(b / n);
    }
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}

const dir = new URL("../web/", import.meta.url);
mkdirSync(dir, { recursive: true });
for (const [name, size] of [["apple-touch-icon.png", 180], ["icon-192.png", 192], ["icon-512.png", 512]]) {
  writeFileSync(new URL(name, dir), png(size));
  console.log(name, size);
}
