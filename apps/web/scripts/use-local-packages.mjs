// Offline recovery: copy installed open-source packages, never application source.
// Standard npm ci remains the preferred installation in a network-enabled environment.
import fs from 'node:fs';
import path from 'node:path';
const source = process.argv[2];
if (!source || !path.isAbsolute(source) || !fs.existsSync(path.join(source, 'next/package.json'))) throw new Error('Supply an absolute existing node_modules directory.');
const root = path.resolve(import.meta.dirname, '..');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const dest = path.join(root, 'node_modules');
fs.mkdirSync(dest, { recursive: true });
const seen = new Set();
const evidence = [];
function locate(name, from = source) {
    let current = from;
    while (current.length > 3) {
        const candidate = path.join(current, name);
        if (fs.existsSync(path.join(candidate, 'package.json'))) return candidate;
        const nested = path.join(current, 'node_modules', name);
        if (fs.existsSync(path.join(nested, 'package.json'))) return nested;
        current = path.dirname(current);
    }
    return null;
}
function copy(dir) {
    if (seen.has(dir)) return;
    seen.add(dir);
    const relative = path.relative(source, dir);
    if (relative.startsWith('..')) throw new Error('Dependency escaped source package directory');
    const pkg = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'));
    if (pkg.os && !pkg.os.includes(process.platform) && !pkg.os.includes('!' + process.platform)) return;
    if (pkg.cpu && !pkg.cpu.includes(process.arch) && !pkg.cpu.includes('!' + process.arch)) return;
    fs.cpSync(dir, path.join(dest, relative), { recursive: true, force: false, errorOnExist: false });
    evidence.push({ name: pkg.name, version: pkg.version, source: dir });
    for (const name of Object.keys(pkg.dependencies || {})) {
        const found = locate(name, path.join(dir, 'node_modules'));
        if (!found) throw new Error(`Missing ${name} required by ${pkg.name}`);
        copy(found);
    }
    for (const name of Object.keys(pkg.optionalDependencies || {})) {
        const found = locate(name, path.join(dir, 'node_modules'));
        if (found) copy(found);
    }
}
for (const [name, version] of Object.entries({ ...manifest.dependencies, ...manifest.devDependencies })) {
    const found = locate(name);
    if (!found) throw new Error(`Missing root package ${name}`);
    const actual = JSON.parse(fs.readFileSync(path.join(found, 'package.json'), 'utf8')).version;
    if (actual !== version) throw new Error(`${name}: expected ${version}, found ${actual}`);
    copy(found);
}
fs.mkdirSync(path.join(root, 'evidence'), { recursive: true });
fs.writeFileSync(path.join(root, 'evidence', 'offline-package-provenance.json'), JSON.stringify({ generatedAt: new Date().toISOString(), packages: evidence }, null, 2));
console.log(`Copied ${evidence.length} installed open-source packages for offline build.`);
