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
  zones: number;
  maxBrightness: number;
}

export interface ColoresState {
  device: DeviceInfo;
  capabilities: Capabilities;
  power: boolean;
  brightness: number;
  color: RGB;
}
