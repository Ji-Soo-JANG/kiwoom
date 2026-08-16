import { readdir, stat } from 'node:fs/promises';
import { resolve } from 'node:path';

const assets = resolve('../src/main/resources/static/assets');
const files = await readdir(assets);
const measured = await Promise.all(
  files
    .filter((file) => file.endsWith('.js'))
    .map(async (file) => ({ file, bytes: (await stat(resolve(assets, file))).size }))
);
const total = measured.reduce((sum, item) => sum + item.bytes, 0);
const oversized = measured.filter((item) => item.bytes > 450_000);
if (oversized.length || total > 750_000) {
  throw new Error(`번들 크기 상한 초과: total=${total}, oversized=${JSON.stringify(oversized)}`);
}
console.log(`Bundle size OK: ${total} bytes across ${measured.length} JavaScript files`);
