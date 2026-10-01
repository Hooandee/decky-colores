const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("emulator", {
  choosePlugin: () => ipcRenderer.invoke("emulator:choose-plugin"),
  loadPath: (path) => ipcRenderer.invoke("emulator:load-path", path),
  selectModel: (model) => ipcRenderer.invoke("emulator:select-model", model),
  rpc: (name, args = []) => ipcRenderer.invoke("emulator:rpc", name, args),
  videoFrame: (frame) => ipcRenderer.send("emulator:video-frame", frame),
  audioPcm: (pcm) => ipcRenderer.send("emulator:audio-pcm", pcm),
  stop: () => ipcRenderer.invoke("emulator:stop"),
  onEvent: (listener) => {
    const fn = (_event, value) => listener(value);
    ipcRenderer.on("emulator:event", fn);
    return () => ipcRenderer.removeListener("emulator:event", fn);
  },
  onLog: (listener) => {
    const fn = (_event, value) => listener(value);
    ipcRenderer.on("emulator:log", fn);
    return () => ipcRenderer.removeListener("emulator:log", fn);
  },
});
