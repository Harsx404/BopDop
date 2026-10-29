// Original indexed pixel sprites. Every drawing operation writes whole logical pixels.
// No generated-image resampling, smoothing, blur, filters, or image-editing input.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const OUT = path.resolve(__dirname, '../../output/halloween-concepts/v1');
const SIZE = 64;
const PALETTE = [
  '#000000', '#20172b', '#31223e', '#49345f',
  '#75529b', '#a788c8', '#d9c8e8', '#f4e4c1',
  '#fff5de', '#8b3f32', '#c45a2c', '#f18a36',
  '#ffc45b', '#526343', '#89934e', '#e1ae83',
].map(h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16)));

function canvas(w = SIZE, h = SIZE, fill = 0) {
  return { w, h, data: new Uint8Array(w * h).fill(fill) };
}
function pixel(c, x, y, color) {
  x = Math.round(x); y = Math.round(y);
  if (x >= 0 && y >= 0 && x < c.w && y < c.h) c.data[y * c.w + x] = color;
}
function rect(c, x, y, w, h, color) {
  for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) pixel(c, i, j, color);
}
function line(c, x0, y0, x1, y1, color) {
  x0 = Math.round(x0); y0 = Math.round(y0); x1 = Math.round(x1); y1 = Math.round(y1);
  const dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
  const dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
  let err = dx + dy;
  while (true) {
    pixel(c, x0, y0, color);
    if (x0 === x1 && y0 === y1) break;
    const e = 2 * err;
    if (e >= dy) { err += dy; x0 += sx; }
    if (e <= dx) { err += dx; y0 += sy; }
  }
}
function poly(c, points, color) {
  const minY = Math.max(0, Math.min(...points.map(p => p[1])));
  const maxY = Math.min(c.h - 1, Math.max(...points.map(p => p[1])));
  for (let y = minY; y <= maxY; y++) {
    const xs = [];
    for (let i = 0; i < points.length; i++) {
      const a = points[i], b = points[(i + 1) % points.length];
      if ((a[1] <= y && b[1] > y) || (b[1] <= y && a[1] > y))
        xs.push(a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]));
    }
    xs.sort((a, b) => a - b);
    for (let i = 0; i + 1 < xs.length; i += 2)
      for (let x = Math.ceil(xs[i]); x <= Math.floor(xs[i + 1]); x++) pixel(c, x, y, color);
  }
  for (let i = 0; i < points.length; i++) line(c, ...points[i], ...points[(i + 1) % points.length], color);
}
function maskShape(c, shape, fill, shade) {
  const m = canvas(c.w, c.h); shape(m);
  for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
    if (!m.data[y * c.w + x]) continue;
    const interior = [[1, 0], [-1, 0], [0, 1], [0, -1]].every(([dx, dy]) =>
      x + dx >= 0 && x + dx < c.w && y + dy >= 0 && y + dy < c.h && m.data[(y + dy) * c.w + x + dx]);
    pixel(c, x, y, interior ? (shade ? shade(x, y) : fill) : 1);
  }
}
function polygon(c, points, fill, shade) {
  maskShape(c, m => poly(m, points, 1), fill, shade);
}
function oval(c, cx, cy, rx, ry, fill, shade) {
  maskShape(c, m => {
    for (let y = cy - ry; y <= cy + ry; y++) for (let x = cx - rx; x <= cx + rx; x++)
      if ((x - cx) ** 2 / rx ** 2 + (y - cy) ** 2 / ry ** 2 <= 1) pixel(m, x, y, 1);
  }, fill, shade);
}
function headphones(c, x, y) {
  // Stepped headband and a near earcup, fixed in character space.
  polygon(c, [[x - 12, y + 10], [x - 12, y + 2], [x - 8, y - 3], [x - 2, y - 6],
    [x + 5, y - 6], [x + 10, y - 3], [x + 12, y], [x + 9, y],
    [x + 6, y - 3], [x - 2, y - 3], [x - 7, y], [x - 9, y + 5], [x - 9, y + 10]], 11);
  line(c, x - 7, y - 2, x - 2, y - 4, 12);
  polygon(c, [[x - 15, y + 6], [x - 12, y + 3], [x - 8, y + 3], [x - 6, y + 6],
    [x - 6, y + 16], [x - 9, y + 19], [x - 13, y + 19], [x - 15, y + 16]], 10,
    (px, py) => px > x - 10 ? 9 : py < y + 8 ? 12 : 11);
  rect(c, x - 13, y + 7, 2, 8, 12);
  rect(c, x - 7, y + 7, 2, 8, 2);
}
function eyes(c, x, y, sleepy, blink = false) {
  if (sleepy || blink) {
    line(c, x, y + 2, x + 2, y + 4, 1); line(c, x + 2, y + 4, x + 5, y + 2, 1);
    line(c, x + 11, y + 2, x + 13, y + 4, 1); line(c, x + 13, y + 4, x + 16, y + 2, 1);
  } else {
    rect(c, x + 1, y, 4, 7, 1); pixel(c, x, y + 2, 1); pixel(c, x + 4, y - 1, 1);
    rect(c, x + 12, y, 4, 7, 1); pixel(c, x + 11, y + 2, 1);
    pixel(c, x + 3, y + 1, 8); pixel(c, x + 14, y + 1, 8);
    pixel(c, x + 2, y + 5, 12); pixel(c, x + 13, y + 5, 12);
  }
}
const BOB = [0, -1, -2, -1, 0, 1, 2, 1];
const STEP = [0, 1, 2, 1, 0, -1, -2, -1];

function wisp(frame, rest = false) {
  const c = canvas(), s = rest ? Math.min(frame, 3) / 3 : 0;
  const breath = rest && frame >= 4 ? [0, -1, 0, 0][frame - 4] : 0;
  const y = rest ? Math.round(2 * s) + breath : BOB[frame];
  // The far earcup sits behind the round head. Hands stay below the face;
  // neither a hand nor an eye may protrude as a nose/muzzle.
  polygon(c, [[43, 22 + y], [46, 22 + y], [48, 25 + y], [48, 32 + y],
    [45, 35 + y], [43, 33 + y]], 10, x => x > 46 ? 9 : 11);
  const pts = [[27, 15], [37, 15], [42, 18], [45, 23], [45, 35], [43, 41],
    [39, 46], [34, 49], [27, 51], [21, 50], [16, 47], [14, 43], [15, 40],
    [17, 40], [18, 44], [22, 46], [27, 46], [28, 44], [25, 40], [22, 35],
    [21, 30], [22, 23], [24, 18]];
  const tailX = rest ? Math.round(3 * s) : [0, 1, 2, 2, 1, 0, -1, -1][frame];
  polygon(c, pts.map(([x, py]) => [x + (py >= 40 && x < 24 ? tailX : 0), py + y]), 7, (x, py) =>
    py > 47 + y || (x < 24 && py > 35 + y) ? 5 : x < 26 || py > 43 + y ? 6 : x > 30 && py < 24 + y ? 8 : 7);
  headphones(c, 31, 19 + y);
  eyes(c, 27, 26 + y, s > 0.3, !rest && frame === 6);
  if (s > 0.3) {
    line(c, 33, 36 + y, 35, 37 + y, 1); line(c, 35, 37 + y, 37, 36 + y, 1);
    line(c, 29, 40 + y, 32, 41 + y, 5); line(c, 32, 41 + y, 36, 39 + y, 6);
  } else {
    rect(c, 33, 35 + y, 4, 3, 1); rect(c, 34, 37 + y, 2, 1, 10);
    pixel(c, 27, 33 + y, 15); pixel(c, 43, 33 + y, 15);
    const handY = [0, 0, 1, 1, 0, 0, -1, -1][frame];
    polygon(c, [[43, 39 + y + handY], [46, 39 + y + handY], [48, 41 + y + handY],
      [47, 43 + y + handY], [44, 44 + y + handY], [42, 42 + y + handY]], 7);
    line(c, 26, 39 + y, 28, 41 + y, 6);
  }
  return c;
}

function ember(frame, rest = false) {
  const c = canvas(), s = rest ? Math.min(frame, 3) / 3 : 0;
  const y = rest ? Math.round(3 * s) + (frame === 5 ? -1 : 0) : [0, -1, 0, 1, 0, -1, 0, 1][frame];
  const stride = rest ? 0 : STEP[frame];
  polygon(c, [[24, 30 + y], [37, 30 + y], [47, 47], [42, 46], [42, 50],
    [35, 48], [27, 50], [19, 48], [12, 50], [17, 42]], 2, (x, py) => x < 22 ? 3 : x > 36 ? 1 : 2);
  polygon(c, [[25, 34 + y], [37, 34 + y], [39, 46], [34, 50], [24, 47]], 3,
    x => x < 31 ? 3 : 2);
  polygon(c, [[24 + stride, 46], [29 + stride, 46], [30 + stride, 50],
    [27 + stride, 53], [20 + stride, 53], [20 + stride, 51]], 11, (x, py) => py > 51 ? 10 : 11);
  polygon(c, [[35 - stride, 47], [40 - stride, 46], [44 - stride, 50],
    [44 - stride, 53], [35 - stride, 53], [33 - stride, 51]], 11, (x, py) => py > 51 ? 10 : 11);
  const armLift = rest ? -Math.round(s) : [0, -1, -1, 0, 1, 1, 0, 0][frame];
  polygon(c, [[18, 36 + y + armLift], [23, 35 + y + armLift], [27, 38 + y + armLift], [25, 42 + y + armLift],
    [20, 42 + y + armLift], [17, 39 + y + armLift]], 11, x => x < 21 ? 10 : 11);
  polygon(c, [[43, 30 + y], [47, 30 + y], [50, 34 + y], [49, 38 + y],
    [45, 39 + y], [42, 36 + y]], 11, (x, py) => py < 34 + y ? 12 : 11);
  oval(c, 33, 23 + y, 16, 14, 11, (x, py) =>
    x < 22 ? 9 : x < 27 ? 10 : x > 44 ? 10 : py < 16 + y ? 12 : 11);
  // Rib shading follows stepped clusters, not continuous shading.
  line(c, 27, 12 + y, 24, 20 + y, 10); line(c, 24, 20 + y, 25, 30 + y, 10);
  line(c, 36, 11 + y, 39, 19 + y, 10); line(c, 39, 19 + y, 39, 32 + y, 10);
  polygon(c, [[30, 12 + y], [31, 7 + y], [27, 5 + y], [27, 3 + y],
    [32, 3 + y], [35, 7 + y], [35, 12 + y]], 14, x => x < 32 ? 14 : 13);
  if (s > 0.3) {
    line(c, 28, 23 + y, 31, 25 + y, 1); line(c, 31, 25 + y, 34, 23 + y, 1);
    line(c, 40, 23 + y, 42, 25 + y, 1); line(c, 42, 25 + y, 45, 23 + y, 1);
    line(c, 32, 31 + y, 37, 33 + y, 12); line(c, 37, 33 + y, 42, 30 + y, 12);
  } else {
    poly(c, [[28, 25 + y], [31, 18 + y], [35, 25 + y]], 1);
    poly(c, [[40, 24 + y], [43, 18 + y], [46, 24 + y]], 1);
    rect(c, 31, 22 + y, 2, 2, 12); rect(c, 43, 21 + y, 2, 2, 12);
    polygon(c, [[29, 29 + y], [35, 31 + y], [43, 28 + y], [43, 31 + y],
      [38, 35 + y], [34, 35 + y], [30, 32 + y]], 12);
    pixel(c, 34, 31 + y, 10); pixel(c, 39, 32 + y, 10);
  }
  polygon(c, [[29, 38 + y], [32, 36 + y], [35, 39 + y], [32, 42 + y]], 12);
  return c;
}

function echo(frame, rest = false) {
  const c = canvas(), s = rest ? Math.min(frame, 3) / 3 : 0;
  const y = rest ? 1 + (frame === 5 ? -1 : 0) : BOB[frame];
  const flap = rest ? Math.round(10 * s) : [0, -5, -10, -5, 0, 5, 9, 5][frame];
  const wingY = 21 + flap + y;
  const reach = rest ? 0 : [0, 1, 2, 2, 1, 0, -1, -1][frame];
  const fold = rest ? 1 - s * 0.65 : 1;
  const wing = points => points.map(([x, py]) => [Math.round(32 + (x - 32) * fold), py]);
  polygon(c, wing([[25, 31 + y], [18, 24 + y], [5 - reach, wingY], [7 - reach, wingY + 8], [12, wingY + 11],
    [12, wingY + 17], [18, wingY + 16], [22, 43 + y], [28, 41 + y]]), 4,
    (x, py) => x < 13 ? 5 : py < 31 + y ? 4 : 3);
  line(c, ...wing([[24, 34 + y]])[0], ...wing([[8 - reach, wingY + 3]])[0], 3);
  line(c, ...wing([[23, 35 + y]])[0], ...wing([[13, wingY + 12]])[0], 3);
  polygon(c, wing([[39, 31 + y], [45, 25 + y], [58 + reach, wingY + 1], [56 + reach, wingY + 9], [51, wingY + 11],
    [51, wingY + 17], [45, wingY + 17], [40, 43 + y], [35, 41 + y]]), 4,
    (x, py) => x > 50 ? 5 : py < 31 + y ? 4 : 3);
  line(c, ...wing([[39, 34 + y]])[0], ...wing([[55 + reach, wingY + 4]])[0], 3);
  line(c, ...wing([[41, 35 + y]])[0], ...wing([[50, wingY + 12]])[0], 3);
  polygon(c, [[21, 25 + y], [19, 9 + y], [22, 7 + y], [29, 14 + y], [30, 25 + y]], 4);
  polygon(c, [[36, 24 + y], [41, 11 + y], [47, 7 + y], [45, 25 + y]], 4);
  poly(c, [[22, 12 + y], [23, 22 + y], [27, 22 + y]], 7);
  poly(c, [[42, 14 + y], [39, 22 + y], [43, 22 + y]], 7);
  oval(c, 33, 39 + y, 11, 12, 3, (x, py) => x < 30 ? 4 : py > 44 + y ? 2 : 3);
  oval(c, 33, 29 + y, 14, 12, 4, (x, py) => py < 23 + y ? 5 : x > 40 ? 3 : 4);
  headphones(c, 32, 20 + y);
  if (s > 0.3) {
    eyes(c, 29, 29 + y, true);
    polygon(c, [[23, 38 + y], [32, 43 + y], [33, 50 + y], [25, 47 + y]], 4, x => x < 28 ? 5 : 4);
    polygon(c, [[42, 37 + y], [34, 43 + y], [33, 50 + y], [41, 47 + y]], 3, x => x > 38 ? 4 : 3);
  } else {
    eyes(c, 29, 28 + y, false, frame === 6);
    rect(c, 35, 38 + y, 5, 3, 1); pixel(c, 35, 38 + y, 8); pixel(c, 39, 38 + y, 8);
    pixel(c, 37, 41 + y, 10);
  }
  return c;
}

function blit(dst, src, dx, dy, scale = 1, transparent = true) {
  for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) {
    const p = src.data[y * src.w + x];
    if (!transparent || p) rect(dst, dx + x * scale, dy + y * scale, scale, scale, p);
  }
}
function crc32(buf) {
  let crc = 0xffffffff;
  for (const b of buf) { crc ^= b; for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1)); }
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const name = Buffer.from(type), n = Buffer.alloc(4), crc = Buffer.alloc(4);
  n.writeUInt32BE(data.length); crc.writeUInt32BE(crc32(Buffer.concat([name, data])));
  return Buffer.concat([n, name, data, crc]);
}
function png(c, filename) {
  const header = Buffer.alloc(13); header.writeUInt32BE(c.w, 0); header.writeUInt32BE(c.h, 4);
  header[8] = 8; header[9] = 3; // Indexed PNG, exactly the shared 16-entry palette.
  const raw = Buffer.alloc((c.w + 1) * c.h);
  for (let y = 0; y < c.h; y++) Buffer.from(c.data.subarray(y * c.w, (y + 1) * c.w)).copy(raw, y * (c.w + 1) + 1);
  fs.writeFileSync(path.join(OUT, filename), Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header),
    chunk('PLTE', Buffer.from(PALETTE.flat())), chunk('tRNS', Buffer.from(PALETTE.map((_, i) => i ? 255 : 0))),
    chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
  ]));
}
function word(n) { const b = Buffer.alloc(2); b.writeUInt16LE(n); return b; }
function gif(frames, filename, delay) {
  const first = frames[0];
  const palette = Buffer.alloc(32 * 3); Buffer.from(PALETTE.flat()).copy(palette);
  const blocks = [Buffer.from('GIF89a'), word(first.w), word(first.h), Buffer.from([0xf4, 2, 0]), palette,
    Buffer.from([0x21, 0xff, 11]), Buffer.from('NETSCAPE2.0'), Buffer.from([3, 1, 0, 0, 0])];
  for (const f of frames) {
    blocks.push(Buffer.from([0x21, 0xf9, 4, 4]), word(delay), Buffer.from([0, 0]));
    blocks.push(Buffer.from([0x2c]), word(0), word(0), word(f.w), word(f.h), Buffer.from([0, 5]));
    // Literal LZW with frequent clear codes: deterministic, small indexed-art previews.
    const codes = [];
    for (let k = 0; k < f.data.length; k += 24) {
      codes.push(32); for (let j = k; j < Math.min(k + 24, f.data.length); j++) codes.push(f.data[j] || 2);
    }
    codes.push(33);
    const packed = []; let bits = 0, count = 0;
    for (const code of codes) { bits |= code << count; count += 6; while (count >= 8) { packed.push(bits & 255); bits >>>= 8; count -= 8; } }
    if (count) packed.push(bits & 255);
    for (let j = 0; j < packed.length; j += 255) { const b = Buffer.from(packed.slice(j, j + 255)); blocks.push(Buffer.from([b.length]), b); }
    blocks.push(Buffer.from([0]));
  }
  blocks.push(Buffer.from([0x3b])); fs.writeFileSync(path.join(OUT, filename), Buffer.concat(blocks));
}
function bounds(c) {
  let x0 = c.w, y0 = c.h, x1 = -1, y1 = -1;
  for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) if (c.data[y * c.w + x]) {
    x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
  }
  return [x0, y0, x1 + 1, y1 + 1];
}

const sprites = { wisp, ember, echo }, animations = {}, validations = [];
fs.mkdirSync(path.join(OUT, 'frames'), { recursive: true });
for (const [name, draw] of Object.entries(sprites)) {
  const frames = [...Array.from({ length: 8 }, (_, i) => draw(i)), ...Array.from({ length: 8 }, (_, i) => draw(i, true))];
  const sheet = canvas(256, 256), preview = canvas(1024, 1024, 2);
  frames.forEach((f, i) => {
    blit(sheet, f, i % 4 * 64, Math.floor(i / 4) * 64);
    blit(preview, f, i % 4 * 256, Math.floor(i / 4) * 256, 4);
    const b = bounds(f);
    if (b[0] < 2 || b[1] < 2 || b[2] > 62 || b[3] > 62) throw new Error(`${name} frame ${i} exceeds safe bounds ${b}`);
    png(f, `frames/${name}-${String(i).padStart(2, '0')}.png`);
  });
  for (let j = 0; j <= 1024; j += 256) {
    line(preview, Math.min(j, 1023), 0, Math.min(j, 1023), 1023, 3);
    line(preview, 0, Math.min(j, 1023), 1023, Math.min(j, 1023), 3);
  }
  png(sheet, `${name}-sheet.png`); png(preview, `${name}-grid-preview.png`);
  const enlarged = frames.map(f => { const p = canvas(256, 256, 2); blit(p, f, 0, 0, 4); return p; });
  gif(enlarged.slice(0, 8), `${name}-walk.gif`, 10);
  gif(enlarged.slice(12), `${name}-rest.gif`, 40);
  animations[name] = { cell: [64, 64], sheet: [256, 256], columns: 4, rows: 4,
    order: 'row-major', padding: 0, gutter: 0, filter: 'nearest',
    playing: { frames: [0, 1, 2, 3, 4, 5, 6, 7], frameMs: 100, loop: true },
    settle: { frames: [8, 9, 10, 11], frameMs: 160, loop: false },
    resting: { frames: [12, 13, 14, 15], frameMs: 400, loop: true },
    scrub: 'Use playing frames with integer-pixel translation; mirror the whole sprite for reverse scrubbing.' };
  validations.push({ name, frameCount: frames.length, dimensions: '64x64', sheetGrid: '4x4', sheetDimensions: '256x256',
    paletteEntriesIncludingTransparency: 16, usedIndices: [...new Set(sheet.data)].sort((a, b) => a - b),
    alphaValues: [0, 255], pixelGrid: '1x1 logical pixels', antialiasing: false,
    bounds: frames.map(bounds), distinctPlayingFrames: new Set(frames.slice(0, 8).map(f => Buffer.from(f.data).toString('base64'))).size });
}
const roster = canvas(768, 512, 2);
Object.values(sprites).forEach((draw, i) => { blit(roster, draw(0), i * 256, 0, 4); blit(roster, draw(4, true), i * 256, 256, 4); });
for (let x = 0; x < 768; x += 256) line(roster, x, 0, x, 511, 3);
line(roster, 0, 256, 767, 256, 3); png(roster, 'character-roster.png');
const allWalk = Array.from({ length: 8 }, (_, f) => {
  const c = canvas(768, 256, 2); Object.values(sprites).forEach((draw, i) => blit(c, draw(f), i * 256, 0, 4)); return c;
});
const allRest = Array.from({ length: 4 }, (_, f) => {
  const c = canvas(768, 256, 2); Object.values(sprites).forEach((draw, i) => blit(c, draw(f + 4, true), i * 256, 0, 4)); return c;
});
gif(allWalk, 'characters-playing.gif', 10); gif(allRest, 'characters-resting.gif', 40);
fs.writeFileSync(path.join(OUT, 'animation-manifest.json'), JSON.stringify({ version: 1, palette: PALETTE, characters: animations }, null, 2));
fs.writeFileSync(path.join(OUT, 'pixel-validation.json'), JSON.stringify(validations, null, 2));
if (process.argv.includes('--android')) {
  const resources = path.resolve(__dirname, '../../app/src/main/res/drawable-nodpi');
  for (const name of Object.keys(sprites)) fs.copyFileSync(path.join(OUT, `${name}-sheet.png`), path.join(resources, `halloween_${name}.png`));
}
console.log(JSON.stringify({ destination: OUT, characters: validations.map(({ name, frameCount, distinctPlayingFrames }) => ({ name, frameCount, distinctPlayingFrames })), palette: 16, squareCell: 64 }, null, 2));
