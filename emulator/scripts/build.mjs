import { build } from "esbuild";
import { cp, mkdir, rm } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const out = join(root, "dist");

await rm(out, { recursive: true, force: true });
await mkdir(out, { recursive: true });
await build({
  entryPoints: [join(root, "renderer", "app.jsx")],
  outfile: join(out, "renderer.js"),
  bundle: true,
  format: "iife",
  platform: "browser",
  target: "chrome138",
  sourcemap: true,
  define: { "process.env.NODE_ENV": JSON.stringify("production") },
});
await Promise.all([
  cp(join(root, "renderer", "index.html"), join(out, "index.html")),
  cp(join(root, "renderer", "styles.css"), join(out, "styles.css")),
  cp(join(root, "assets"), join(out, "assets"), { recursive: true }),
  cp(join(root, "main.mjs"), join(out, "main.mjs")),
  cp(join(root, "preload.cjs"), join(out, "preload.cjs")),
  cp(join(root, "plugin-package.mjs"), join(out, "plugin-package.mjs")),
]);
