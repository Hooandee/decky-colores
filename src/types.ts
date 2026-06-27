export interface RGB {
  r: number;
  g: number;
  b: number;
}

export interface DeviceInfo {
  name: string;
  board: string;
  product: string;
}

export interface ZoneGroup {
  name: string;
  region: number[];
  zones: number[];
}

export interface Capabilities {
  color: boolean;
  brightness: boolean;
  effects: boolean;
  ambilight: boolean;
  zones: number;
  maxBrightness: number;
  layout: ZoneGroup[];
}

export type Mode = "solid" | "gradient" | "effect" | "ambient";

export interface AmbilightState {
  saturation: number;
  smoothing: number;
  fps: number;
}

export type EffectId = "breathing" | "rainbow" | "wave" | "cycle";

export interface EffectState {
  id: EffectId;
  speed: number;
}

export interface ColoresState {
  device: DeviceInfo;
  capabilities: Capabilities;
  power: boolean;
  brightness: number;
  mode: Mode;
  color: RGB;
  gradient: RGB[];
  effect: EffectState;
  ambilight: AmbilightState;
}

export interface GradientPreset {
  name: string;
  stops: RGB[];
}

export type EffectColorNeed = "color" | "gradient" | "none";

export interface EffectMeta {
  id: EffectId;
  label: string;
  description: string;
  colors: RGB[];
  needs: EffectColorNeed;
}
