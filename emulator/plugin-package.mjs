import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import AdmZip from "adm-zip";

export const MAX_FILES = 4096;
export const MAX_UNPACKED_BYTES = 64 * 1024 * 1024;
export const REQUIRED = [
  "plugin.json",
  "package.json",
  "main.py",
  "dist/index.js",
  "py_modules/armada_rgb_profiles.json",
];

function safeParts(name) {
  if (!name || name.includes("\\") || name.startsWith("/") || /^[A-Za-z]:/.test(name)) return null;
  const parts = name.split("/").filter(Boolean);
  if (parts.some((part) => part === "." || part === "..")) return null;
  return parts;
}

export function inspectPluginZip(zipPath) {
  const zip = new AdmZip(zipPath);
  const entries = zip.getEntries();
  if (!entries.length || entries.length > MAX_FILES) throw new Error("Invalid number of ZIP entries");
  let total = 0;
  let top = null;
  for (const entry of entries) {
    const parts = safeParts(entry.entryName);
    if (!parts) throw new Error(`Unsafe ZIP path: ${entry.entryName}`);
    if (parts.length) top = top === null ? parts[0] : top === parts[0] ? top : "";
    total += Number(entry.header.size) || 0;
    if (total > MAX_UNPACKED_BYTES) throw new Error("ZIP is larger than 64 MiB unpacked");
    const unixMode = (entry.attr >>> 16) & 0xffff;
    if ((unixMode & 0o170000) === 0o120000) throw new Error(`Symbolic links are not allowed: ${entry.entryName}`);
  }
  if (!top) throw new Error("The ZIP must have one top-level plugin directory");
  const names = new Set(entries.map((entry) => entry.entryName.replace(/\/$/, "")));
  for (const required of REQUIRED) {
    if (!names.has(`${top}/${required}`)) throw new Error(`Plugin is missing ${required}`);
  }
  const readJson = (relative) => {
    const entry = zip.getEntry(`${top}/${relative}`);
    try {
      return JSON.parse(entry.getData().toString("utf8"));
    } catch {
      throw new Error(`Invalid JSON in ${relative}`);
    }
  };
  const manifest = readJson("plugin.json");
  const pkg = readJson("package.json");
  const catalog = readJson("py_modules/armada_rgb_profiles.json");
  if (manifest.name !== "Colores") throw new Error("Only Colores plugin ZIPs are accepted");
  if (catalog.version !== 1 || !Array.isArray(catalog.profiles)) throw new Error("Unsupported Armada RGB catalog");
  const models = [];
  for (const profile of catalog.profiles) {
    if (!Array.isArray(profile.models) || !profile.backend || !Array.isArray(profile.backend.targets)) {
      throw new Error("Invalid Armada RGB profile");
    }
    for (const model of profile.models) models.push({ model, profile });
  }
  const hash = crypto.createHash("sha256").update(fs.readFileSync(zipPath)).digest("hex");
  return { zip, top, manifest, pkg, catalog, models, hash, fileName: path.basename(zipPath) };
}

export function extractInspectedPlugin(inspected, destination) {
  for (const entry of inspected.zip.getEntries()) {
    const parts = safeParts(entry.entryName);
    if (!parts || !parts.length) continue;
    const relative = parts.slice(1);
    if (!relative.length) continue;
    const output = path.join(destination, ...relative);
    const resolved = path.resolve(output);
    if (!resolved.startsWith(`${path.resolve(destination)}${path.sep}`)) throw new Error("Unsafe extraction target");
    if (entry.isDirectory) {
      fs.mkdirSync(output, { recursive: true });
    } else {
      fs.mkdirSync(path.dirname(output), { recursive: true });
      fs.writeFileSync(output, entry.getData(), { mode: 0o600 });
    }
  }
}
