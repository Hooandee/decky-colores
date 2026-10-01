import { app, BrowserWindow, dialog, ipcMain, net, protocol } from "electron";
import { spawn } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { extractInspectedPlugin, inspectPluginZip } from "./plugin-package.mjs";

// SteamOS/Armada images do not provide Electron's setuid sandbox and commonly
// disable unprivileged user namespaces. Renderer context isolation remains on;
// release ZIPs must still be treated as trusted executable code.
app.commandLine.appendSwitch("no-sandbox");

const here = path.dirname(fileURLToPath(import.meta.url));
const developmentRoot = path.resolve(here, "..");
const packagedRoot = process.resourcesPath;
let windowRef = null;
let pluginRoot = null;
let pluginInfo = null;
let worker = null;
let workerBuffer = "";
let nextRequest = 1;
let generation = 0;
const pending = new Map();
const temporaryPlugins = new Set();

protocol.registerSchemesAsPrivileged([{ scheme: "colores-plugin", privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true } }]);

function send(channel, value) {
  if (windowRef && !windowRef.isDestroyed()) windowRef.webContents.send(channel, value);
}

function stopWorker() {
  if (!worker) return;
  try { worker.stdin.write(`${JSON.stringify({ type: "unload" })}\n`); } catch {}
  const old = worker;
  worker = null;
  setTimeout(() => { if (!old.killed) old.kill("SIGTERM"); }, 1500);
  for (const { reject } of pending.values()) reject(new Error("Plugin worker stopped"));
  pending.clear();
}

function workerPython() {
  const bundled = path.join(packagedRoot, "runtime", "python", "bin", "python3");
  return fs.existsSync(bundled) ? bundled : (process.env.COLORES_EMULATOR_PYTHON || "python3");
}

function workerScript() {
  return app.isPackaged
    ? path.join(process.resourcesPath, "app.asar.unpacked", "worker", "worker.py")
    : path.join(developmentRoot, "worker", "worker.py");
}

function handleWorkerLine(line) {
  let message;
  try { message = JSON.parse(line); } catch { send("emulator:log", { level: "error", message: line }); return; }
  if (message.id && pending.has(message.id)) {
    const request = pending.get(message.id);
    pending.delete(message.id);
    message.ok ? request.resolve(message.result) : request.reject(new Error(message.error || "Worker request failed"));
    return;
  }
  send("emulator:event", message);
}

function startWorker(model, restoreConfig = null) {
  stopWorker();
  workerBuffer = "";
  const settingsDir = path.join(app.getPath("userData"), "profiles", pluginInfo.hash.slice(0, 12), encodeURIComponent(model));
  fs.mkdirSync(settingsDir, { recursive: true });
  const child = spawn(workerPython(), [workerScript()], { stdio: ["pipe", "pipe", "pipe"], env: { PATH: "/usr/bin:/bin", LANG: "C.UTF-8", PYTHONUNBUFFERED: "1" } });
  worker = child;
  child.stdout.setEncoding("utf8");
  child.stdout.on("data", (chunk) => {
    workerBuffer += chunk;
    let newline;
    while ((newline = workerBuffer.indexOf("\n")) >= 0) {
      const line = workerBuffer.slice(0, newline).trim();
      workerBuffer = workerBuffer.slice(newline + 1);
      if (line) handleWorkerLine(line);
    }
  });
  child.stderr.setEncoding("utf8");
  child.stderr.on("data", (message) => send("emulator:log", { level: "info", message: message.trim() }));
  child.on("exit", (code, signal) => {
    if (worker === child) {
      send("emulator:event", { type: "worker_exit", code, signal });
      worker = null;
    }
  });
  child.stdin.write(`${JSON.stringify({ type: "init", pluginRoot, settingsDir, model, restoreConfig })}\n`);
}

function requestWorker(type, payload = {}) {
  if (!worker) return Promise.reject(new Error("No plugin model is running"));
  const id = nextRequest++;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    worker.stdin.write(`${JSON.stringify({ id, type, ...payload })}\n`);
  });
}

function restoreConfigFromState(state) {
  const profile = state?.profileContext?.profile;
  if (!profile) return null;
  return {
    profile,
    power: state.power,
    chargerOnly: state.chargerOnly,
    forceControl: state.forceControl,
    powerLedOff: state.powerLedOff,
    powerLedAwakeOff: state.powerLedAwakeOff,
    powerLedSuspendOff: state.powerLedSuspendOff,
    sleepChargingIndicator: state.sleepChargingIndicator,
    savedGradients: state.savedGradients || [],
    sensorBands: state.sensorBands || {},
    rememberStartup: state.rememberStartup,
  };
}

async function captureRestoreConfig() {
  if (!worker) return null;
  try {
    return restoreConfigFromState(await requestWorker("rpc", { name: "get_state", args: [] }));
  } catch (error) {
    send("emulator:log", { level: "warning", message: `Could not preserve active configuration: ${error.message}` });
    return null;
  }
}

async function loadZip(zipPath) {
  const inspected = inspectPluginZip(zipPath);
  stopWorker();
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), "colores-emulator-"));
  temporaryPlugins.add(tempRoot);
  extractInspectedPlugin(inspected, tempRoot);
  pluginRoot = tempRoot;
  generation += 1;
  pluginInfo = {
    fileName: inspected.fileName,
    hash: inspected.hash,
    packageVersion: String(inspected.pkg.version || "unknown"),
    apiVersion: inspected.manifest.api_version ?? null,
    models: inspected.models,
    generation,
  };
  return pluginInfo;
}

app.whenReady().then(() => {
  protocol.handle("colores-plugin", (request) => {
    if (!pluginRoot) return new Response("No plugin loaded", { status: 404 });
    const requested = decodeURIComponent(new URL(request.url).pathname).replace(/^\/+/, "");
    const target = path.resolve(pluginRoot, requested);
    if (!target.startsWith(`${path.resolve(pluginRoot)}${path.sep}`)) return new Response("Forbidden", { status: 403 });
    return net.fetch(pathToFileURL(target).toString());
  });

  windowRef = new BrowserWindow({
    width: 1500,
    height: 940,
    minWidth: 1050,
    minHeight: 700,
    backgroundColor: "#080a0f",
    webPreferences: { preload: path.join(here, "preload.cjs"), contextIsolation: true, sandbox: true },
  });
  windowRef.loadFile(path.join(here, "index.html"));
});

ipcMain.handle("emulator:choose-plugin", async () => {
  const picked = await dialog.showOpenDialog(windowRef, { properties: ["openFile"], filters: [{ name: "Colores ZIP", extensions: ["zip"] }] });
  if (picked.canceled) return null;
  return loadZip(picked.filePaths[0]);
});
ipcMain.handle("emulator:load-path", (_event, zipPath) => loadZip(zipPath));
ipcMain.handle("emulator:select-model", async (_event, model) => {
  if (!pluginInfo?.models.some((entry) => entry.model === model)) throw new Error("Model is not present in the loaded ZIP");
  const restoreConfig = await captureRestoreConfig();
  startWorker(model, restoreConfig);
  return true;
});
ipcMain.handle("emulator:rpc", (_event, name, args) => requestWorker("rpc", { name, args }));
ipcMain.on("emulator:video-frame", (_event, frame) => { if (worker) worker.stdin.write(`${JSON.stringify({ type: "video_frame", ...frame })}\n`); });
ipcMain.on("emulator:audio-pcm", (_event, pcm) => { if (worker) worker.stdin.write(`${JSON.stringify({ type: "audio_pcm", pcm })}\n`); });
ipcMain.handle("emulator:stop", () => stopWorker());

app.on("before-quit", () => {
  stopWorker();
  for (const directory of temporaryPlugins) {
    try { fs.rmSync(directory, { recursive: true, force: true }); } catch {}
  }
});
app.on("window-all-closed", () => app.quit());
