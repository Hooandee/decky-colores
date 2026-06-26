import { callable } from "@decky/api";
import { ColoresState } from "./types";

export const getState = callable<[], ColoresState>("get_state");
export const setColor = callable<[r: number, g: number, b: number], void>("set_color");
export const setBrightness = callable<[value: number], void>("set_brightness");
export const setPower = callable<[on: boolean], void>("set_power");
