import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test, { afterEach } from "node:test";
import AdmZip from "adm-zip";
import { inspectPluginZip } from "../plugin-package.mjs";

const temporaryDirectories = new Set();
afterEach(() => {
  for (const directory of temporaryDirectories) fs.rmSync(directory, { recursive: true, force: true });
  temporaryDirectories.clear();
});

function fixture(overrides = {}) {
  const zip = new AdmZip();
  const files = {
    "Colores/plugin.json": JSON.stringify({ name: "Colores", api_version: 1 }),
    "Colores/package.json": JSON.stringify({ version: "9.9.9" }),
    "Colores/main.py": "class Plugin: pass\n",
    "Colores/dist/index.js": "export default () => ({})\n",
    "Colores/py_modules/armada_rgb_profiles.json": JSON.stringify({ version: 1, profiles: [{ models: ["Test Handheld"], backend: { type: "multicolor", targets: ["rgb:test"] } }] }),
    ...overrides,
  };
  for (const [name, value] of Object.entries(files)) zip.addFile(name, Buffer.from(value));
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "colores-zip-test-"));
  temporaryDirectories.add(directory);
  const output = path.join(directory, "fixture.zip"); zip.writeZip(output); return output;
}

test("accepts a Colores release and lists catalog models", () => {
  const result = inspectPluginZip(fixture());
  assert.equal(result.pkg.version, "9.9.9");
  assert.deepEqual(result.models.map(({ model }) => model), ["Test Handheld"]);
  assert.match(result.hash, /^[a-f0-9]{64}$/);
});

test("rejects a different Decky plugin", () => {
  assert.throws(() => inspectPluginZip(fixture({ "Colores/plugin.json": JSON.stringify({ name: "Other" }) })), /Only Colores/);
});

test("rejects an unsupported catalog", () => {
  assert.throws(() => inspectPluginZip(fixture({ "Colores/py_modules/armada_rgb_profiles.json": JSON.stringify({ version: 2, profiles: [] }) })), /Unsupported/);
});
