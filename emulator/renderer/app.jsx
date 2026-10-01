import React, { useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import * as ReactDOM from "react-dom";
import * as jsxRuntime from "react/jsx-runtime";

globalThis.SP_REACT = React;
globalThis.SP_REACTDOM = ReactDOM;
globalThis.SP_JSX = jsxRuntime;

const modalNode = document.getElementById("decky-modal-root");
const modalRoot = createRoot(modalNode);
let closeActiveModal = () => modalRoot.render(null);

function cleanProps(props) {
  const { onActivate, onGamepadFocus, onGamepadBlur, preferredFocus, noFocusRing, flowChildren, focusClassName, ...rest } = props;
  return {
    ...rest,
    onClick: rest.onClick || onActivate,
    onFocus: rest.onFocus || onGamepadFocus,
    onBlur: rest.onBlur || onGamepadBlur,
    onKeyDown: (event) => {
      rest.onKeyDown?.(event);
      if ((event.key === "Enter" || event.key === " ") && onActivate) onActivate(event);
    },
  };
}

const Focusable = React.forwardRef(({ children, ...props }, ref) => <div ref={ref} tabIndex={props.tabIndex ?? 0} {...cleanProps(props)}>{children}</div>);
const PanelSection = ({ children, ...props }) => <section className="decky-section" {...props}>{children}</section>;
const PanelSectionRow = ({ children, ...props }) => <div className="decky-row" {...props}>{children}</div>;
const DialogButton = ({ children, ...props }) => <button type="button" className="decky-button" {...cleanProps(props)}>{children}</button>;
const ButtonItem = ({ children, description, ...props }) => <button type="button" className="decky-button decky-button-item" {...cleanProps(props)}><span>{children}</span>{description && <small>{description}</small>}</button>;
const ToggleField = ({ label, description, checked, onChange, disabled }) => <label className={`decky-field toggle ${disabled ? "disabled" : ""}`}><span><b>{label}</b>{description && <small>{description}</small>}</span><input type="checkbox" checked={Boolean(checked)} disabled={disabled} onChange={(event) => onChange?.(event.target.checked)} /></label>;
const SliderField = ({ label, value = 0, min = 0, max = 100, step = 1, valueSuffix = "", showValue, disabled, onChange }) => <label className={`decky-field slider ${disabled ? "disabled" : ""}`}><span><b>{label}</b>{showValue && <output>{value}{valueSuffix}</output>}</span><input type="range" value={value} min={min} max={max} step={step} disabled={disabled} onChange={(event) => onChange?.(Number(event.target.value))} /></label>;
const TextField = ({ label, value, onChange, multiline, ...props }) => <label className="decky-field"><span><b>{label}</b></span>{multiline ? <textarea value={value} onChange={onChange} {...props} /> : <input value={value} onChange={onChange} {...props} />}</label>;
const Dropdown = ({ rgOptions = [], selectedOption, onChange, menuLabel }) => <label className="decky-field"><span><b>{menuLabel}</b></span><select value={selectedOption} onChange={(event) => onChange?.(rgOptions.find((option) => String(option.data) === event.target.value))}>{rgOptions.map((option) => <option key={String(option.data)} value={String(option.data)}>{typeof option.label === "string" ? option.label : String(option.data)}</option>)}</select></label>;
const Spinner = ({ width = 28, height = 28 }) => <span className="decky-spinner" style={{ width, height }} />;
const ModalRoot = ({ children, closeModal, onCancel }) => <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) (closeModal || onCancel || closeActiveModal)(); }}><div className="modal-card">{children}</div></div>;
class ErrorBoundary extends React.Component {
  constructor(props) { super(props); this.state = { error: null }; }
  static getDerivedStateFromError(error) { return { error }; }
  render() { return this.state.error ? <div className="plugin-error">{String(this.state.error)}</div> : this.props.children; }
}
function showModal(element) {
  const close = () => { modalRoot.render(null); };
  closeActiveModal = close;
  modalRoot.render(React.cloneElement(element, { closeModal: close }));
  return { Close: close };
}

globalThis.DFL = {
  PanelSection, PanelSectionRow, SliderField, ToggleField, ButtonItem, Focusable,
  Spinner, ErrorBoundary, showModal, DialogButton, ModalRoot, TextField, Dropdown,
  staticClasses: { Title: "decky-title" },
  Router: { MainRunningApp: null },
  Navigation: { Navigate: () => undefined },
  getFocusNavController: () => ({ Focus: () => undefined }),
};
globalThis.SteamClient = {
  GameSessions: { RegisterForAppLifetimeNotifications: () => ({ unregister() {} }) },
  User: { RegisterForPrepareForSystemSuspendProgress: () => ({ unregister() {} }) },
  Input: {},
};
window.__DECKY_SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED_deckyLoaderAPIInit = {
  connect(version) {
    return {
      _version: version,
      callable: (name) => (...args) => window.emulator.rpc(name, args),
      toaster: { toast: ({ title, body }) => console.info("toast", title, body) },
    };
  },
};

const MODEL_GEOMETRY = {
  "Retroid Pocket 5": "symmetric", "Retroid Pocket 5 Visionox": "symmetric",
  "Retroid Pocket Flip2": "symmetric", "Retroid Pocket Flip2 Visionox": "symmetric",
  "Retroid Pocket Nova": "symmetric", "MANGMI Pocket Max": "symmetric",
};

function zoneCount(profile) {
  const { type, targets } = profile.backend;
  return type === "multicolor" ? targets.length : targets.filter((target) => target.startsWith("red=")).length;
}

function ringGradient(colors) {
  if (!colors.length) return "#151821";
  if (colors.length === 1) return `rgb(${colors[0].join(" ")})`;
  return `conic-gradient(${colors.map((color, index) => `rgb(${color.join(" ")}) ${(index / colors.length) * 100}% ${((index + 1) / colors.length) * 100}%`).join(",")})`;
}

function Device({ model, profile, led, canvasRef, videoRef, audioRef, mediaKind }) {
  const colors = led?.colors || Array.from({ length: zoneCount(profile) }, () => [0, 0, 0]);
  const symmetric = MODEL_GEOMETRY[model] === "symmetric";
  const isOdin2 = model === "AYN Odin 2";
  const single = colors.length === 1;
  const half = Math.ceil(colors.length / 2);
  const left = single ? colors : isOdin2 ? [colors[0]] : colors.slice(0, half);
  const right = single ? colors : isOdin2 ? [colors[2] || colors[0]] : colors.slice(half);
  const leftSide = isOdin2 ? colors[1] : null;
  const rightSide = isOdin2 ? colors[3] : null;
  return <div className="device-stage">
    <div className="handheld">
      <div className="side-led left" style={{ background: leftSide ? `rgb(${leftSide.join(" ")})` : "transparent" }} />
      <div className="side-led right" style={{ background: rightSide ? `rgb(${rightSide.join(" ")})` : "transparent" }} />
      <div className="screen-shell">
        <canvas ref={canvasRef} width="640" height="360" />
        <video ref={videoRef} className={mediaKind === "video" ? "source-media" : "hidden"} playsInline controls />
        <audio ref={audioRef} hidden preload="auto" />
        {mediaKind === "audio" && <div className="audio-cover"><span>♫</span><b>Audio source</b></div>}
        <div className={`capture-guide ${single ? "full" : "left"}`}><span>{single ? "global" : "L"}</span></div>
        {!single && <div className="capture-guide right"><span>R</span></div>}
      </div>
      <div className={`stick left-stick ${symmetric ? "low" : "high"}`}><div className="led-ring" style={{ background: ringGradient(left) }} /><i /></div>
      <div className={`dpad ${symmetric ? "high" : "low"}`}><i /><i /></div>
      <div className="stick right-stick low"><div className="led-ring" style={{ background: ringGradient(right) }} /><i /></div>
      <div className="buttons"><i>A</i><i>B</i><i>X</i><i>Y</i></div>
      <div className="device-name">{model}</div>
    </div>
  </div>;
}

function bytesToBase64(bytes) {
  let binary = "";
  const size = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += size) binary += String.fromCharCode(...bytes.subarray(offset, offset + size));
  return btoa(binary);
}

function drawPattern(ctx, pattern, elapsed) {
  const { width, height } = ctx.canvas;
  ctx.fillStyle = "#080a10"; ctx.fillRect(0, 0, width, height);
  if (pattern === "edges") {
    const gradient = ctx.createLinearGradient(0, 0, width, 0);
    gradient.addColorStop(0, "#ff1f3d"); gradient.addColorStop(.35, "#180b22"); gradient.addColorStop(.65, "#07152b"); gradient.addColorStop(1, "#1677ff");
    ctx.fillStyle = gradient; ctx.fillRect(0, 0, width, height);
  } else if (pattern === "quadrants") {
    ["#ff2d55", "#38d96b", "#247cff", "#ffd43b"].forEach((color, index) => { ctx.fillStyle = color; ctx.fillRect((index % 2) * width / 2, Math.floor(index / 2) * height / 2, width / 2, height / 2); });
  } else if (pattern === "sweep") {
    const x = ((elapsed / 3500) % 1) * width;
    ctx.fillStyle = "#090b12"; ctx.fillRect(0, 0, width, height);
    const gradient = ctx.createRadialGradient(x, height / 2, 0, x, height / 2, width * .3);
    gradient.addColorStop(0, `hsl(${(elapsed / 15) % 360} 100% 60%)`); gradient.addColorStop(1, "transparent");
    ctx.fillStyle = gradient; ctx.fillRect(0, 0, width, height);
  } else if (pattern === "pulse") {
    const light = 18 + (Math.sin(elapsed / 180) + 1) * 30;
    ctx.fillStyle = `hsl(285 90% ${light}%)`; ctx.fillRect(0, 0, width, height);
  } else {
    ctx.fillStyle = "#10141f"; ctx.fillRect(0, 0, width, height);
    ctx.fillStyle = "#778099"; ctx.font = "600 26px system-ui"; ctx.textAlign = "center"; ctx.fillText("Choose a test source", width / 2, height / 2);
  }
}

function App() {
  const [info, setInfo] = useState(null);
  const [model, setModel] = useState("");
  const [backendVersion, setBackendVersion] = useState("");
  const [led, setLed] = useState(null);
  const [status, setStatus] = useState("Load a Colores release ZIP");
  const [pattern, setPattern] = useState("edges");
  const [mediaKind, setMediaKind] = useState("pattern");
  const [logs, setLogs] = useState([]);
  const [playing, setPlaying] = useState(false);
  const [mediaSource, setMediaSource] = useState(null);
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(0);
  const pluginRootRef = useRef(null);
  const pluginDefinition = useRef(null);
  const canvasRef = useRef(null);
  const videoRef = useRef(null);
  const audioRef = useRef(null);
  const activeMediaRef = useRef(null);
  const mediaUrlRef = useRef(null);
  const sampleRef = useRef(document.createElement("canvas"));
  const audioContextRef = useRef(null);
  const audioNodesRef = useRef(new WeakSet());
  const fileInputRef = useRef(null);

  const selected = useMemo(() => info?.models.find((entry) => entry.model === model), [info, model]);

  async function unmountPlugin() {
    try { pluginDefinition.current?.onDismount?.(); } catch (error) { console.warn(error); }
    pluginDefinition.current = null;
    pluginRootRef.current?.unmount();
    pluginRootRef.current = null;
    document.getElementById("plugin-content").replaceChildren();
  }

  async function mountPlugin(generation) {
    await unmountPlugin();
    const module = await import(`colores-plugin://active/dist/index.js?generation=${generation}-${Date.now()}`);
    const definition = module.default();
    pluginDefinition.current = definition;
    pluginRootRef.current = createRoot(document.getElementById("plugin-content"));
    pluginRootRef.current.render(definition.content);
  }

  useEffect(() => {
    const offEvent = window.emulator.onEvent(async (event) => {
      if (event.type === "ready") {
        setBackendVersion(event.backendVersion || "unknown");
        setStatus("Plugin running against virtual hardware");
        try { await mountPlugin(info?.generation); } catch (error) { setStatus(`Frontend failed: ${error.message}`); }
      } else if (event.type === "led_state") setLed(event);
      else if (event.type === "fatal") setStatus(`Backend failed: ${event.error}`);
      else if (event.type === "worker_exit") setStatus(`Backend exited (${event.code ?? event.signal})`);
    });
    const offLog = window.emulator.onLog((entry) => setLogs((current) => [...current.slice(-39), entry.message].filter(Boolean)));
    return () => { offEvent(); offLog(); void unmountPlugin(); };
  }, [info?.generation]);

  useEffect(() => {
    if (!model) return;
    setLed(null); setBackendVersion(""); setStatus(`Starting ${model}…`);
    void unmountPlugin().then(() => window.emulator.selectModel(model)).catch((error) => setStatus(error.message));
  }, [model, info?.generation]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    const sample = sampleRef.current; sample.width = 64; sample.height = 36;
    const sampleCtx = sample.getContext("2d", { willReadFrequently: true });
    let alive = true; let previous = 0;
    const frame = (time) => {
      if (!alive) return;
      if (mediaKind === "pattern") drawPattern(ctx, pattern, time);
      else if (mediaKind === "video" && videoRef.current?.readyState >= 2) ctx.drawImage(videoRef.current, 0, 0, canvas.width, canvas.height);
      if (time - previous >= 33) {
        previous = time;
        sampleCtx.drawImage(canvas, 0, 0, 64, 36);
        const rgba = sampleCtx.getImageData(0, 0, 64, 36).data;
        const rgb = new Uint8Array(64 * 36 * 3);
        for (let source = 0, target = 0; source < rgba.length; source += 4) { rgb[target++] = rgba[source]; rgb[target++] = rgba[source + 1]; rgb[target++] = rgba[source + 2]; }
        window.emulator.videoFrame({ width: 64, height: 36, data: bytesToBase64(rgb) });
      }
      requestAnimationFrame(frame);
    };
    requestAnimationFrame(frame);
    return () => { alive = false; };
  }, [mediaKind, pattern, model]);

  useEffect(() => {
    const mediaElements = [audioRef.current, videoRef.current].filter(Boolean);
    const cleanups = mediaElements.map((media) => {
      const isActive = () => activeMediaRef.current === media;
      const sync = () => {
        if (!isActive()) return;
        setPosition(media.currentTime || 0);
        setDuration(Number.isFinite(media.duration) ? media.duration : 0);
      };
      const onPlay = () => {
        if (!isActive()) return;
        setPlaying(true);
        setMediaSource((source) => source ? { ...source, status: "Playing", error: null } : source);
      };
      const onPause = () => {
        if (!isActive()) return;
        setPlaying(false);
        setMediaSource((source) => source ? { ...source, status: "Paused" } : source);
      };
      const onEnded = () => {
        if (!isActive()) return;
        setPlaying(false);
        setMediaSource((source) => source ? { ...source, status: "Finished" } : source);
      };
      const onError = () => {
        if (!isActive()) return;
        const code = media.error?.code;
        const reasons = {
          1: "Playback was interrupted.",
          2: "The file could not be read.",
          3: "The audio/video file could not be decoded.",
          4: "This audio/video format is not supported.",
        };
        setPlaying(false);
        setMediaSource((source) => source ? { ...source, status: "Error", error: reasons[code] || "The file could not be played." } : source);
      };
      media.addEventListener("timeupdate", sync);
      media.addEventListener("durationchange", sync);
      media.addEventListener("loadedmetadata", sync);
      media.addEventListener("play", onPlay);
      media.addEventListener("pause", onPause);
      media.addEventListener("ended", onEnded);
      media.addEventListener("error", onError);
      return () => {
        media.removeEventListener("timeupdate", sync);
        media.removeEventListener("durationchange", sync);
        media.removeEventListener("loadedmetadata", sync);
        media.removeEventListener("play", onPlay);
        media.removeEventListener("pause", onPause);
        media.removeEventListener("ended", onEnded);
        media.removeEventListener("error", onError);
      };
    });
    return () => cleanups.forEach((cleanup) => cleanup());
  }, [model, info?.generation]);

  useEffect(() => () => {
    activeMediaRef.current?.pause();
    if (mediaUrlRef.current) URL.revokeObjectURL(mediaUrlRef.current);
  }, []);

  function connectAudio(element) {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    const context = audioContextRef.current || new AudioCtx({ sampleRate: 16000 });
    audioContextRef.current = context;
    if (audioNodesRef.current.has(element)) return true;
    const source = context.createMediaElementSource(element);
    try {
      const processor = context.createScriptProcessor(1024, 1, 1);
      source.connect(processor);
      processor.connect(context.destination);
      processor.onaudioprocess = (event) => {
        const input = event.inputBuffer.getChannelData(0);
        const output = event.outputBuffer.getChannelData(0);
        const pcm = new Int16Array(input.length);
        for (let i = 0; i < input.length; i++) {
          output[i] = input[i];
          pcm[i] = Math.max(-32768, Math.min(32767, Math.round(input[i] * 32767)));
        }
        window.emulator.audioPcm(bytesToBase64(new Uint8Array(pcm.buffer)));
      };
      audioNodesRef.current.add(element);
      return true;
    } catch (error) {
      source.disconnect();
      source.connect(context.destination);
      console.warn("Audio will play without VU capture:", error);
      return false;
    }
  }

  function activeMedia() {
    return activeMediaRef.current;
  }

  async function playMedia(media) {
    if (!media) return;
    try {
      const connected = connectAudio(media);
      if (!connected) setMediaSource((source) => source ? { ...source, warning: "VU capture is unavailable; playback will continue without it." } : source);
      const resume = audioContextRef.current?.resume();
      if (resume) void resume.catch((error) => {
        console.warn("Audio capture context could not resume:", error);
        setMediaSource((source) => source ? { ...source, warning: "The audio context could not start; VU capture may be unavailable." } : source);
      });
    } catch (error) {
      console.warn("Audio capture is unavailable; trying normal playback:", error);
      setMediaSource((source) => source ? { ...source, warning: "VU capture is unavailable; playback will continue without it." } : source);
    }
    try {
      await media.play();
      setPlaying(true);
      setMediaSource((source) => source ? { ...source, status: "Playing", error: null } : source);
    } catch (error) {
      const message = error?.name === "NotSupportedError"
        ? "This audio/video format is not supported."
        : error?.name === "NotAllowedError"
          ? "Playback was blocked. Press Play to try again."
          : error?.message || "The file could not be played.";
      setPlaying(false);
      setMediaSource((source) => source ? { ...source, status: "Error", error: message } : source);
    }
  }

  function stopCurrentMedia() {
    const previous = activeMedia();
    activeMediaRef.current = null;
    setPlaying(false);
    previous?.pause();
    for (const media of [audioRef.current, videoRef.current]) {
      if (!media) continue;
      media.pause();
      media.removeAttribute("src");
      media.load();
    }
    if (mediaUrlRef.current) URL.revokeObjectURL(mediaUrlRef.current);
    mediaUrlRef.current = null;
  }

  function openMedia(event) {
    const input = event.currentTarget;
    const file = input.files?.[0];
    input.value = "";
    if (!file) return;
    stopCurrentMedia();

    const extension = file.name.split(".").pop()?.toLowerCase();
    const videoExtensions = new Set(["avi", "mkv", "mov", "mp4", "m4v", "ogv", "webm"]);
    const audioExtensions = new Set(["aac", "flac", "m4a", "mp3", "oga", "ogg", "opus", "wav", "weba"]);
    const kind = file.type.startsWith("video/") || videoExtensions.has(extension)
      ? "video"
      : file.type.startsWith("audio/") || audioExtensions.has(extension)
        ? "audio"
        : null;
    if (!kind) {
      setMediaSource({ name: file.name, status: "Error", error: "Could not identify this file as audio or video." });
      setMediaKind("pattern");
      setPattern("none");
      return;
    }

    const media = kind === "audio" ? audioRef.current : videoRef.current;
    if (!media) {
      setMediaSource({ name: file.name, status: "Error", error: "The media player is not ready." });
      return;
    }
    const url = URL.createObjectURL(file);
    mediaUrlRef.current = url;
    media.dataset.source = "file";
    media.loop = false;
    media.src = url;
    activeMediaRef.current = media;
    setMediaSource({ name: file.name, kind, status: "Loading", error: null, warning: null });
    setPosition(0);
    setDuration(0);
    setMediaKind(kind);
    setPattern("none");
    void playMedia(media);
  }

  function toggleMedia() {
    const media = activeMedia();
    if (!media) return;
    if (media.paused) void playMedia(media);
    else media.pause();
  }

  function toggleDefaultTrack() {
    const media = audioRef.current;
    if (!media) return;
    if (activeMedia() === media && !media.paused && media.dataset.source === "default-track") {
      media.pause();
      return;
    }
    stopCurrentMedia();
    media.dataset.source = "default-track";
    media.src = new URL("assets/hella-bumps.mp3", window.location.href).toString();
    media.loop = true;
    activeMediaRef.current = media;
    setMediaKind("audio");
    setPattern("none");
    setPosition(0);
    setDuration(0);
    setMediaSource({ name: "Hella Bumps (default track)", kind: "audio", status: "Loading", error: null, warning: null });
    void playMedia(media);
  }

  async function choosePlugin() {
    try {
      const loaded = await window.emulator.choosePlugin(); if (!loaded) return;
      setInfo(loaded); setModel(loaded.models[0]?.model || ""); setStatus("ZIP validated");
    } catch (error) { setStatus(`Cannot load ZIP: ${error.message}`); }
  }

  return <main>
    <header>
      <div><h1>Colores Emulator</h1><p>{status}</p></div>
      <button className="primary" onClick={choosePlugin}>Load Colores ZIP</button>
    </header>
    {info ? <>
      <div className="metadata">
        <label>Model<select value={model} onChange={(event) => setModel(event.target.value)}>{info.models.map((entry) => <option key={entry.model}>{entry.model}</option>)}</select></label>
        <div><span>Package</span><b>{info.packageVersion}</b></div><div><span>Backend</span><b className={backendVersion && backendVersion !== info.packageVersion ? "mismatch" : ""}>{backendVersion || "…"}</b></div>
        <div><span>Decky API</span><b>{String(info.apiVersion ?? "—")}</b></div><div className="hash"><span>SHA-256</span><code title={info.hash}>{info.hash.slice(0, 16)}…</code></div>
      </div>
      <div className="workspace">
        <section className="simulator">
          {selected && <Device model={model} profile={selected.profile} led={led} canvasRef={canvasRef} videoRef={videoRef} audioRef={audioRef} mediaKind={mediaKind} />}
          <div className="media-controls">
            <label>Video pattern<select value={pattern} onChange={(event) => { setPattern(event.target.value); setMediaKind("pattern"); }}><option value="edges">Red/blue edges</option><option value="quadrants">Quadrants</option><option value="sweep">Color sweep</option><option value="pulse">Fast pulse</option><option value="none">Idle</option></select></label>
            <button onClick={() => fileInputRef.current?.click()}>Open audio/video</button>
            <input ref={fileInputRef} hidden type="file" accept="audio/*,video/*" onChange={openMedia} />
            {mediaSource && <>
              <button onClick={toggleMedia}>{playing ? "Pause" : "Play"}</button>
              <label className="seek">Position<input type="range" min="0" max={duration || 1} step="0.1" value={Math.min(position, duration || 1)} onChange={(event) => { const media = activeMedia(); if (media) media.currentTime = Number(event.target.value); }} /></label>
            </>}
            <button className={playing && activeMedia()?.dataset.source === "default-track" ? "active" : ""} onClick={toggleDefaultTrack}>{playing && activeMedia()?.dataset.source === "default-track" ? "Stop default music" : "Play default music"}</button>
          </div>
          {mediaSource && <div className={`media-source-info ${mediaSource.error ? "error" : ""}`}><b title={mediaSource.name}>{mediaSource.name}</b><span>{mediaSource.status}</span>{mediaSource.error && <p>{mediaSource.error}</p>}{mediaSource.warning && <p>{mediaSource.warning}</p>}</div>}
          <details><summary>Virtual hardware output</summary><pre>{JSON.stringify(led, null, 2)}</pre></details>
          <details><summary>Backend log</summary><pre>{logs.join("\n") || "No messages"}</pre></details>
        </section>
        <aside className="plugin-panel"><div className="panel-heading"><span>Loaded plugin UI</span><b>{info.fileName}</b></div><div id="plugin-content" /></aside>
      </div>
    </> : <section className="empty"><div className="empty-device">◉ ▬ ◉</div><h2>Load a release to begin</h2><p>The plugin will run against isolated, virtual Armada RGB hardware.</p></section>}
  </main>;
}

createRoot(document.getElementById("root")).render(<App />);
