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

export interface Capabilities {
  color: boolean;
  brightness: boolean;
  effects: boolean;
  zones: number;
  maxBrightness: number;
}

export type Mode = "solid" | "gradient" | "effect";

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
}

export interface GradientPreset {
  name: string;
  stops: RGB[];
}

export interface EffectMeta {
  id: EffectId;
  label: string;
  description: string;
  colors: RGB[];
}
