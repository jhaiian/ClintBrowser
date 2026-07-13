// Extracts uBlock Origin's built-in scriptlets by importing the real
// scriptlets.js module graph under Node, so `fn.toString()` gives back
// the exact source uBO itself registers at runtime.
//
// Usage: node extract-scriptlets.mjs <path-to-uBO>/src/js/resources <output.json>
import { pathToFileURL } from 'node:url';
import { writeFileSync } from 'node:fs';
import path from 'node:path';

const [, , resourcesDir, outPath] = process.argv;
if (!resourcesDir || !outPath) {
    console.error('Usage: node extract-scriptlets.mjs <uBO>/src/js/resources <output.json>');
    process.exit(1);
}

const modUrl = pathToFileURL(path.join(resourcesDir, 'scriptlets.js')).href;
const { builtinScriptlets } = await import(modUrl);

const out = builtinScriptlets.map(s => ({
    name: s.name,
    aliases: Array.isArray(s.aliases) ? s.aliases : [],
    code: s.fn.toString(),
    dependencies: Array.isArray(s.dependencies) ? s.dependencies : [],
    requiresTrust: s.requiresTrust === true,
}));

writeFileSync(outPath, JSON.stringify(out, null, 2));
console.log('Extracted', out.length, 'scriptlet resources');
