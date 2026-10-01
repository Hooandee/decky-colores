const fs = require("node:fs");
const path = require("node:path");

/**
 * SteamOS/Armada kernels disable unprivileged user namespaces and an AppImage
 * cannot ship a root-owned setuid chrome-sandbox. Keep Electron's renderer
 * context isolation, but launch Chromium with its process sandbox disabled.
 */
exports.default = async function afterPack(context) {
  if (context.electronPlatformName !== "linux") return;
  const executable = path.join(context.appOutDir, "colores-emulator");
  const binary = `${executable}-bin`;
  fs.renameSync(executable, binary);
  fs.writeFileSync(
    executable,
    '#!/bin/sh\nexec "$(dirname "$0")/colores-emulator-bin" --no-sandbox "$@"\n',
    { mode: 0o755 },
  );
};
