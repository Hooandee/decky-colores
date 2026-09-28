import { DeviceInfo } from "./types";

export function deviceDisplayName(
  device: DeviceInfo,
  translate: (key: string) => string,
): string {
  return device.displayNameKey ? translate(device.displayNameKey) : device.name;
}
