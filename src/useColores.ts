import { useCallback, useEffect, useRef, useState } from "react";
import { ColoresState, EffectId, Mode, RGB } from "./types";
import * as api from "./api";

function useThrottle<A extends unknown[]>(fn: (...args: A) => void, ms: number) {
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const last = useRef(0);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const latest = useRef<A | undefined>(undefined);

  useEffect(() => () => clearTimeout(timer.current), []);

  return useCallback(
    (...args: A) => {
      latest.current = args;
      const elapsed = Date.now() - last.current;
      if (elapsed >= ms) {
        last.current = Date.now();
        fnRef.current(...args);
      } else if (!timer.current) {
        timer.current = setTimeout(() => {
          last.current = Date.now();
          timer.current = undefined;
          if (latest.current) fnRef.current(...latest.current);
        }, ms - elapsed);
      }
    },
    [ms],
  );
}

export function useColores() {
  const [state, setState] = useState<ColoresState | null>(null);

  useEffect(() => {
    api.getState()
      .then(setState)
      .catch((e) => console.error("Colores: getState failed", e));
  }, []);

  const pushSolid = useThrottle((c: RGB) => api.setSolid(c.r, c.g, c.b), 60);
  const pushBrightness = useThrottle((v: number) => api.setBrightness(v), 60);
  const pushEffect = useThrottle(
    (id: EffectId, speed: number, useGradient: boolean) => api.setEffect(id, speed, useGradient),
    60,
  );
  const pushAmbilight = useThrottle(
    (sat: number, sm: number, fps: number) => api.setAmbilight(sat, sm, fps),
    80,
  );

  const setBrightness = (brightness: number) => {
    setState((s) => (s ? { ...s, brightness } : s));
    pushBrightness(brightness);
  };

  const setPower = (power: boolean) => {
    setState((s) => (s ? { ...s, power } : s));
    api.setPower(power);
  };

  const setMode = (mode: Mode) => {
    setState((s) => (s ? { ...s, mode } : s));
    api.setMode(mode);
  };

  const setColor = (color: RGB) => {
    setState((s) => (s ? { ...s, color } : s));
    pushSolid(color);
  };

  const setGradient = (gradient: RGB[]) => {
    setState((s) => (s ? { ...s, gradient } : s));
    api.setGradient(gradient.map((c) => [c.r, c.g, c.b]));
  };

  const setEffectId = (id: EffectId) => {
    setState((s) => (s ? { ...s, effect: { ...s.effect, id } } : s));
    if (state) pushEffect(id, state.effect.speed, state.effect.useGradient);
  };

  const setEffectSpeed = (speed: number) => {
    setState((s) => (s ? { ...s, effect: { ...s.effect, speed } } : s));
    if (state) pushEffect(state.effect.id, speed, state.effect.useGradient);
  };

  const setEffectGradient = (useGradient: boolean) => {
    setState((s) => (s ? { ...s, effect: { ...s.effect, useGradient } } : s));
    if (state) pushEffect(state.effect.id, state.effect.speed, useGradient);
  };

  const setAmbilight = (saturation: number, smoothing: number, fps: number) => {
    setState((s) => (s ? { ...s, ambilight: { saturation, smoothing, fps } } : s));
    pushAmbilight(saturation, smoothing, fps);
  };

  return {
    state,
    setBrightness,
    setPower,
    setMode,
    setColor,
    setGradient,
    setEffectId,
    setEffectSpeed,
    setEffectGradient,
    setAmbilight,
  };
}
