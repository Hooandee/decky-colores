import crypto from "node:crypto";
import fs from "node:fs";
import { mkdir, rm } from "node:fs/promises";
import { pipeline } from "node:stream/promises";
import { Readable } from "node:stream";
import { spawn } from "node:child_process";
import path from "node:path";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const runtime = join(root, "runtime");
const python = join(runtime, "python", "bin", "python3");
const metadataUrl = "https://raw.githubusercontent.com/astral-sh/python-build-standalone/latest-release/latest-release.json";

if (fs.existsSync(python)) {
  console.log(`Using existing runtime: ${python}`);
  process.exit(0);
}

async function json(url) {
  const response = await fetch(url, { headers: { "User-Agent": "colores-emulator-build" } });
  if (!response.ok) throw new Error(`Cannot fetch ${url}: HTTP ${response.status}`);
  return response.json();
}

const latest = await json(metadataUrl);
const release = await json(`https://api.github.com/repos/astral-sh/python-build-standalone/releases/tags/${latest.tag}`);
const pattern = /^cpython-3\.13\.\d+\+\d+-aarch64-unknown-linux-gnu-install_only_stripped\.tar\.gz$/;
const asset = release.assets.find(({ name }) => pattern.test(name));
if (!asset) throw new Error(`Release ${latest.tag} has no supported ARM64 CPython 3.13 runtime`);
if (!String(asset.digest || "").startsWith("sha256:")) throw new Error("Python runtime asset has no SHA-256 digest");

await mkdir(runtime, { recursive: true });
const archive = join(runtime, asset.name);
const response = await fetch(asset.browser_download_url, { redirect: "follow", headers: { "User-Agent": "colores-emulator-build" } });
if (!response.ok || !response.body) throw new Error(`Cannot download Python runtime: HTTP ${response.status}`);
await pipeline(Readable.fromWeb(response.body), fs.createWriteStream(archive, { mode: 0o600 }));
const actual = crypto.createHash("sha256").update(fs.readFileSync(archive)).digest("hex");
const expected = asset.digest.slice("sha256:".length);
if (actual !== expected) {
  await rm(archive, { force: true });
  throw new Error(`Python runtime checksum mismatch: expected ${expected}, got ${actual}`);
}

await new Promise((resolve, reject) => {
  const child = spawn("tar", ["-xzf", archive, "-C", runtime], { stdio: "inherit" });
  child.on("error", reject);
  child.on("exit", (code) => code === 0 ? resolve() : reject(new Error(`tar exited with ${code}`)));
});
await rm(archive, { force: true });
if (!fs.existsSync(python)) throw new Error("Downloaded archive did not contain python/bin/python3");
console.log(`Prepared ${asset.name} (${expected})`);
