import { callable } from "@decky/api";
import { ColoresState, GradientPreset } from "./types";

export const getState = callable<[], ColoresState>("get_state");
export const setPower = callable<[on: boolean], void>("set_power");
export const setChargerOnly = callable<[on: boolean], void>("set_charger_only");
export const setBrightness = callable<[value: number], void>("set_brightness");
export const setMode = callable<[mode: string], void>("set_mode");
export const setSolid = callable<[r: number, g: number, b: number], void>("set_solid");
export const setGradient = callable<[stops: number[][]], void>("set_gradient");
export const setGradientSpeed = callable<[speed: number], void>("set_gradient_speed");
export const setEffect = callable<[id: string, speed: number, useGradient: boolean], void>("set_effect");
export const setAmbilight = callable<[saturation: number, smoothing: number, fps: number], void>("set_ambilight");
export const getAmbilightStatus = callable<[], string>("get_ambilight_status");
export const saveGradient = callable<[name: string, stops: number[][]], GradientPreset[]>("save_gradient");
export const deleteGradient = callable<[name: string], GradientPreset[]>("delete_gradient");
export const getVersion = callable<[], string>("get_version");
export const setExperiment = callable<[feature: string, on: boolean], void>("set_experiment");
export const setPowerLed = callable<[off: boolean], void>("set_power_led");
export const reconnect = callable<[], boolean>("reconnect");
export const setForceControl = callable<[on: boolean], void>("set_force_control");
export const setBatteryBreathe = callable<[on: boolean], void>("set_battery_breathe");

// ── Self-updater ──

export interface UpdateInfo {
  current: string;
  latest: string;
  has_update: boolean;
  notes: string;
  download_url: string;
  error: string;
}

export interface InstallResult {
  ok: boolean;
  needs_restart: boolean;
  message: string;
}

export const checkUpdate = callable<[force: boolean], UpdateInfo>("check_update");
export const installUpdate = callable<[], InstallResult>("install_update");
export const restartLoader = callable<[], void>("restart_loader");

export interface ReportResult {
  ok: boolean;
  code?: string;
  issue_url?: string;
  error?: string;
  saved_path?: string;
}

export const submitReport =
  callable<[categories: string[], text: string], ReportResult>("submit_report");
