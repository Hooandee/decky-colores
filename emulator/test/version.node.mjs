import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

test("emulator release version matches the plugin and Plasmoid version", () => {
  const plugin = JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8"));
  const emulator = JSON.parse(fs.readFileSync(path.join(root, "emulator/package.json"), "utf8"));
  const lock = JSON.parse(fs.readFileSync(path.join(root, "emulator/package-lock.json"), "utf8"));
  assert.equal(emulator.version, plugin.version);
  assert.equal(lock.version, plugin.version);
  assert.equal(lock.packages[""].version, plugin.version);
});
