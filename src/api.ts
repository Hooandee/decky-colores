import { callable } from "@decky/api";
import { ColoresState, GradientPreset } from "./types";

export const getState = callable<[], ColoresState>("get_state");
export const setPower = callable<[on: boolean], void>("set_power");
export const setBrightness = callable<[value: number], void>("set_brightness");
export const setMode = callable<[mode: string], void>("set_mode");
export const setSolid = callable<[r: number, g: number, b: number], void>("set_solid");
export const setGradient = callable<[stops: number[][]], void>("set_gradient");
export const setEffect = callable<[id: string, speed: number, useGradient: boolean], void>("set_effect");
export const setAmbilight = callable<[saturation: number, smoothing: number, fps: number], void>("set_ambilight");
export const getAmbilightStatus = callable<[], string>("get_ambilight_status");
export const saveGradient = callable<[name: string, stops: number[][]], GradientPreset[]>("save_gradient");
export const deleteGradient = callable<[name: string], GradientPreset[]>("delete_gradient");
