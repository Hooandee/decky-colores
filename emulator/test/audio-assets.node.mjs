import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

test("bundled CC0 default music and attribution are present", () => {
  const audio = fs.readFileSync(path.join(root, "assets", "hella-bumps.mp3"));
  const attribution = fs.readFileSync(path.join(root, "assets", "ATTRIBUTION.txt"), "utf8");
  assert.ok(audio.length > 100_000);
  assert.match(audio.subarray(0, 3).toString("ascii"), /^ID3|^\xff\xfb/);
  assert.match(attribution, /CC0 1\.0 Universal/);
  assert.match(attribution, /opengameart\.org\/content\/hella-bumps-menu-music/);
});
