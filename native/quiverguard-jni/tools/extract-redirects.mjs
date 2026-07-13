// Extracts uBlock Origin's redirect-resources.js manifest by importing it
// directly under Node (it's a plain ESM module, no browser APIs needed).
//
// Usage: node extract-redirects.mjs <path-to-uBO>/src/js <output.json>
import { pathToFileURL } from 'node:url';
import { writeFileSync } from 'node:fs';
import path from 'node:path';

const [, , jsDir, outPath] = process.argv;
if (!jsDir || !outPath) {
    console.error('Usage: node extract-redirects.mjs <uBO>/src/js <output.json>');
    process.exit(1);
}

const modUrl = pathToFileURL(path.join(jsDir, 'redirect-resources.js')).href;
const { default: redirectResources } = await import(modUrl);

const out = [...redirectResources].map(([name, meta]) => ({
    name,
    aliases: Array.isArray(meta.alias) ? meta.alias : (meta.alias ? [meta.alias] : []),
}));

writeFileSync(outPath, JSON.stringify(out, null, 2));
console.log('Extracted', out.length, 'redirect resource entries');
