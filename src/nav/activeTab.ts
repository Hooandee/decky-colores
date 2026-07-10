const KEY = "colores:activeTab";
const SENSOR_KEY = "colores:sensorMode";

export function readActiveTab(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function writeActiveTab(id: string): void {
  try {
    localStorage.setItem(KEY, id);
  } catch {
    void 0;
  }
}

export function readSensorMode(): string | null {
  try {
    return localStorage.getItem(SENSOR_KEY);
  } catch {
    return null;
  }
}

export function writeSensorMode(id: string): void {
  try {
    localStorage.setItem(SENSOR_KEY, id);
  } catch {
    void 0;
  }
}
