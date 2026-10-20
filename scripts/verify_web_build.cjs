const fs = require('node:fs');
const path = require('node:path');
const build = path.resolve(__dirname, '../frontend/build');
const index = path.join(build, 'index.html');
if (!fs.existsSync(index)) {
  console.error('Missing frontend/build/index.html. Run npm run build:android successfully before Capacitor sync. No placeholder assets were generated.');
  process.exit(1);
}
const html = fs.readFileSync(index, 'utf8');
const scripts = [...html.matchAll(/<script\b[^>]*src=["']([^"']+)["']/gi)].map(m => m[1]);
if (!scripts.some(src => !/^https?:/i.test(src))) throw new Error('Production index has no bundled JavaScript entry.');
for (const match of html.matchAll(/(?:src|href)=["']([^"']+)["']/gi)) {
  const ref = match[1];
  if (/^(?:https?:|data:|#|\/\/)/i.test(ref)) continue;
  const local = path.resolve(build, decodeURIComponent(ref.split(/[?#]/)[0]).replace(/^\/+/, ''));
  if (!local.startsWith(build + path.sep) || !fs.existsSync(local)) throw new Error(`Missing or unsafe production asset: ${ref}`);
}
console.log('Production HTML and referenced local assets: PASS');
for (const [name, signature] of [['rive.wasm', '0061736d'], ['rive_fallback.wasm', '0061736d'], ['theme-switch.riv', '52495645']]) {
  const bytes = fs.readFileSync(path.join(build, 'animations', name));
  if (bytes.subarray(0, 4).toString('hex') !== signature) throw new Error(`Invalid bundled animation asset: ${name}`);
}
if (!html.includes("script-src 'self' 'wasm-unsafe-eval';") || html.includes("'unsafe-eval'")) throw new Error('CSP must retain self-only scripts while permitting local WebAssembly');
if (!fs.readFileSync(path.join(build, 'open-source-notices.txt'), 'utf8').includes('MIT License')) throw new Error('Missing open-source UI notices');
console.log('Local Rive assets, notices and restricted script policy: PASS');
