import { useCallback, useEffect, useRef, useState } from "react";
import { ColoresState, RGB } from "./types";
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
    api.getState().then(setState);
  }, []);

  const pushColor = useThrottle((c: RGB) => api.setColor(c.r, c.g, c.b), 60);
  const pushBrightness = useThrottle((v: number) => api.setBrightness(v), 60);

  const setColor = (color: RGB) => {
    setState((s) => (s ? { ...s, color } : s));
    pushColor(color);
  };

  const setBrightness = (brightness: number) => {
    setState((s) => (s ? { ...s, brightness } : s));
    pushBrightness(brightness);
  };

  const setPower = (power: boolean) => {
    setState((s) => (s ? { ...s, power } : s));
    api.setPower(power);
  };

  return { state, setColor, setBrightness, setPower };
}
