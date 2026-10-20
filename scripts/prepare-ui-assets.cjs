#!/usr/bin/env node
// Copy the pinned Rive WASM into the standalone app; runtime never uses a CDN.
const fs = require('fs'), path = require('path');
const frontend = path.resolve(__dirname, '../frontend');
const packageFile = require.resolve('@rive-app/canvas/package.json', { paths: [frontend] });
for (const filename of ['rive.wasm', 'rive_fallback.wasm']) {
  const source = path.join(path.dirname(packageFile), filename);
  const target = path.join(frontend, 'public/animations', filename);
  if (!fs.existsSync(source)) throw new Error('Pinned Rive package is missing '+filename);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.copyFileSync(source, target);
  console.log(`Bundled local Rive WASM: ${filename}, ${fs.statSync(target).size} bytes`);
}
fs.copyFileSync(path.join(frontend, '../THIRD_PARTY_NOTICES.md'), path.join(frontend, 'public/open-source-notices.txt'));
