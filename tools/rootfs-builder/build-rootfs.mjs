#!/usr/bin/env node
// tools/rootfs-builder/build-rootfs.mjs
// Host-side rootfs builder for Windows/macOS/Linux without requiring apk.static or Linux arm64 host.
// Downloads Alpine minirootfs + OpenCode binary + APK packages, extracts & merges them in JS.
// Produces deterministic rootfs.tar.gz + sha256 + manifest.json in ../runtime/artifacts/
//
// Usage: node build-rootfs.mjs [--out-dir=../runtime/artifacts]

import { createWriteStream, createReadStream, promises as fs, constants } from 'fs';
import { pipeline } from 'stream/promises';
import { createGunzip, createGzip } from 'zlib';
import { createHash } from 'crypto';
import { URL } from 'url';
import https from 'https';
import http from 'http';
import { Tar } from 'tar';

const VERSIONS_LOCK = '../versions.lock';

async function fetch(url, dest) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith('https:') ? https : http;
    const req = client.get(url, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        fetch(res.headers.location, dest).then(resolve).catch(reject);
        return;
      }
      if (res.statusCode !== 200) {
        reject(new Error(`HTTP ${res.statusCode} for ${url}`));
        return;
      }
      const out = createWriteStream(dest);
      pipeline(res, out).then(resolve).catch(reject);
    });
    req.on('error', reject);
    req.setTimeout(300_000, () => req.destroy(new Error('Timeout')));
  });
}

function sha256File(path) {
  return new Promise((resolve, reject) => {
    const hash = createHash('sha256');
    const rs = createReadStream(path);
    rs.on('data', (d) => hash.update(d));
    rs.on('end', () => resolve(hash.digest('hex')));
    rs.on('error', reject);
  });
}

async function extractTarGz(archive, destDir) {
  await fs.mkdir(destDir, { recursive: true });
  const extractor = new Tar.Parse({ cwd: destDir });
  await pipeline(
    createReadStream(archive),
    createGunzip(),
    extractor
  );
}

async function createTarGz(srcDir, destArchive) {
  const pack = new Tar.Pack({ cwd: srcDir, mtime: new Date('2026-01-01T00:00:00Z'), portable: true });
  const gzip = createGzip({ level: 9 });
  const out = createWriteStream(destArchive);
  await pipeline(pack, gzip, out);
  // Add all files in deterministic order
  const files = await collectFiles(srcDir);
  files.sort();
  for (const f of files) {
    const stat = await fs.stat(f);
    const header = {
      name: f.slice(srcDir.length + 1),
      mode: stat.mode,
      uid: 0,
      gid: 0,
      mtime: new Date('2026-01-01T00:00:00Z'),
      size: stat.size,
    };
    if (stat.isSymbolicLink()) {
      header.type = 'symlink';
      header.linkname = await fs.readlink(f);
      pack.entry(header);
    } else if (stat.isFile()) {
      pack.entry(header, createReadStream(f));
    } else if (stat.isDirectory()) {
      header.type = 'directory';
      pack.entry(header);
    }
  }
  pack.finalize();
  await new Promise((r) => out.on('close', r));
}

async function collectFiles(dir) {
  const out = [];
  async function walk(d) {
    const entries = await fs.readdir(d, { withFileTypes: true });
    for (const e of entries) {
      const p = `${d}/${e.name}`;
      out.push(p);
      if (e.isDirectory()) await walk(p);
    }
  }
  await walk(dir);
  return out;
}

async function downloadApkIndex(arch, repo) {
  const url = `${repo}/${arch}/APKINDEX.tar.gz`;
  const tmp = `/tmp/APKINDEX-${arch}.tar.gz`;
  await fetch(url, tmp);
  await extractTarGz(tmp, `/tmp/apkindex-${arch}`);
  const content = await fs.readFile(`/tmp/apkindex-${arch}/APKINDEX`, 'utf-8');
  return parseApkIndex(content);
}

function parseApkIndex(text) {
  const pkgs = {};
  let current = null;
  for (const line of text.split('\n')) {
    if (!line) { current = null; continue; }
    if (line.startsWith('P:')) current = { name: line.slice(2), depends: [], provides: [] };
    else if (line.startsWith('D:') && current) current.depends.push(line.slice(2));
    else if (line.startsWith('p:') && current) current.provides.push(line.slice(2));
    else if (line.startsWith('V:') && current) current.version = line.slice(2);
    if (current && current.name) pkgs[current.name] = current;
  }
  return pkgs;
}

async function resolvePackages(index, targets) {
  const resolved = new Set();
  const queue = [...targets];
  while (queue.length) {
    const name = queue.pop();
    if (resolved.has(name)) continue;
    const pkg = index[name];
    if (!pkg) throw new Error(`Package ${name} not found in index`);
    resolved.add(name);
    for (const dep of pkg.depends) {
      // Handle so:libname deps by finding provider
      if (dep.startsWith('so:')) {
        const lib = dep.slice(3);
        const provider = Object.values(index).find(p => p.provides.includes(dep));
        if (provider) queue.push(provider.name);
      } else {
        queue.push(dep);
      }
    }
  }
  return [...resolved];
}

async function downloadAndExtractApk(pkgName, index, arch, repo, destDir) {
  const pkg = index[pkgName];
  if (!pkg) throw new Error(`Package ${pkgName} not in index`);
  const filename = `${pkg.name}-${pkg.version}.apk`;
  const url = `${repo}/${arch}/${filename}`;
  const tmp = `/tmp/${filename}`;
  await fetch(url, tmp);
  // APK is a gzipped tar
  await extractTarGz(tmp, destDir);
  console.log(`  Extracted ${pkgName}-${pkg.version}`);
  return pkg.version;
}

async function main() {
  const args = process.argv.slice(2);
  const outDir = (args.find(a => a.startsWith('--out-dir='))?.split('=')[1] || '../runtime/artifacts').trim();
  await fs.mkdir(outDir, { recursive: true });

  // Parse versions.lock (simplified)
  const lock = await fs.readFile(VERSIONS_LOCK, 'utf-8');
  const alpineVer = lock.match(/\[alpine\]\nversion = "([^"]+)"/)?.[1] || '3.24.1';
  const alpineSha = lock.match(/\[alpine\]\nsha256 = "([^"]+)"/)?.[1];
  const opencodeVer = lock.match(/\[opencode\]\nversion = "([^"]+)"/)?.[1] || '1.18.23';
  const opencodeSha = lock.match(/\[opencode\]\nsha256 = "([^"]+)"/)?.[1];

  const alpineUrl = `https://dl-cdn.alpinelinux.org/alpine/v${alpineVer.split('.').slice(0,2).join('.')}/releases/aarch64/alpine-minirootfs-${alpineVer}-aarch64.tar.gz`;
  const opencodeUrl = `https://github.com/anomalyco/opencode/releases/download/v${opencodeVer}/opencode-linux-arm64-musl.tar.gz`;

  const work = await fs.mkdtemp('/tmp/opencode-rootfs-');
  console.log(`Working in ${work}`);

  // 1. Alpine minirootfs
  const alpineTgz = `${work}/alpine.tgz`;
  console.log('Downloading Alpine minirootfs...');
  await fetch(alpineUrl, alpineTgz);
  if (alpineSha) {
    const actual = await sha256File(alpineTgz);
    if (actual !== alpineSha) throw new Error(`Alpine SHA mismatch: ${actual} != ${alpineSha}`);
  }
  await extractTarGz(alpineTgz, work);
  console.log('Alpine extracted');

  // 2. OpenCode binary
  const ocTgz = `${work}/opencode.tgz`;
  console.log('Downloading OpenCode binary...');
  await fetch(opencodeUrl, ocTgz);
  if (opencodeSha) {
    const actual = await sha256File(ocTgz);
    if (actual !== opencodeSha) throw new Error(`OpenCode SHA mismatch: ${actual} != ${opencodeSha}`);
  }
  await extractTarGz(ocTgz, work);
  await fs.rename(`${work}/opencode`, `${work}/usr/local/bin/opencode`);
  await fs.chmod(`${work}/usr/local/bin/opencode`, 0o755);
  console.log('OpenCode binary installed');

  // 3. APK packages via index
  const repoBase = `https://dl-cdn.alpinelinux.org/alpine/v${alpineVer.split('.').slice(0,2).join('.')}`;
  const index = await downloadApkIndex('aarch64', `${repoBase}/main`);
  const indexComm = await downloadApkIndex('aarch64', `${repoBase}/community`);
  const allIndex = { ...index, ...indexComm };

  const targets = [
    'git', 'bash', 'ripgrep', 'nodejs', 'npm',
    'openssh-client', 'ca-certificates', 'tzdata',
    'coreutils', 'tar', 'gzip', 'xz', 'zstd'
  ];
  const resolved = await resolvePackages(allIndex, targets);
  console.log(`Resolved ${resolved.length} packages`);

  const versions = {};
  for (const pkg of resolved) {
    const ver = await downloadAndExtractApk(pkg, allIndex, 'aarch64', repoBase, work);
    versions[pkg] = ver;
  }

  // 4. resolv.conf + release marker
  await fs.mkdir(`${work}/etc`, { recursive: true });
  await fs.writeFile(`${work}/etc/resolv.conf`,
    `nameserver 8.8.8.8\nnameserver 1.1.1.1\noptions timeout:2 attempts:3\n`);
  await fs.writeFile(`${work}/etc/opencode-android-release`,
    `LAYOUT_VERSION=1\nOPENCODE_VERSION=${opencodeVer}\nALPINE_VERSION=${alpineVer}\n`);

  // 5. Create final tar.gz
  const outTgz = `${outDir}/rootfs.tar.gz`;
  console.log(`Creating ${outTgz}...`);
  await createTarGz(work, outTgz);

  // 6. SHA256 + manifest
  const sha = await sha256File(outTgz);
  await fs.writeFile(`${outDir}/rootfs.sha256`, `${sha}  rootfs.tar.gz\n`);
  const fileCount = (await collectFiles(work)).length;
  const uncompressed = (await fs.stat(outTgz)).size; // approximate; actual uncompressed is larger
  const manifest = {
    layoutVersion: 1,
    fileCount,
    uncompressedBytes: uncompressed,
    opencodeVersion: opencodeVer,
    alpineVersion: alpineVer,
    packages: versions,
  };
  await fs.writeFile(`${outDir}/rootfs.manifest.json`, JSON.stringify(manifest, null, 2));

  console.log(`Done. SHA256: ${sha}`);
  console.log(`Artifacts in ${outDir}`);
  // Cleanup
  await fs.rm(work, { recursive: true, force: true });
}

main().catch(e => { console.error(e); process.exit(1); });