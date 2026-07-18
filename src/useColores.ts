import { useCallback, useEffect, useRef, useState } from "react";
import { ColoresState, EffectId, EffectState, Mode, RGB } from "./types";
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
  const [loadError, setLoadError] = useState(false);

  const refreshState = useCallback(() => {
    api.getState()
      .then((s) => {
        setState(s);
        setLoadError(false);
      })
      .catch((e) => {
        console.error("Colores: getState failed", e);
        setLoadError(true);
      });
  }, []);

  useEffect(() => {
    refreshState();
  }, [refreshState]);

  // The RGB node hangs off a USB HID device that can enumerate late on cold boot,
  // so the first getState may report no LEDs. The backend keeps probing for ~25s
  // (ACQUIRE_ATTEMPTS) and rebuilds its capabilities once the node appears, but it
  // never pushes; without this the QAM stays on "no LEDs" until reopened. While the
  // loaded state shows no LEDs, re-fetch every 2s within a bounded window so the UI
  // self-heals. A machine that detects LEDs immediately schedules zero timers.
  const noLeds = !!state && !state.capabilities.color && !state.capabilities.brightness;
  const acquireDeadline = useRef<number | null>(null);
  useEffect(() => {
    if (!noLeds) {
      acquireDeadline.current = null;
      return;
    }
    if (acquireDeadline.current === null) acquireDeadline.current = Date.now() + 30000;
    if (Date.now() >= acquireDeadline.current) return;
    const timer = setTimeout(refreshState, 2000);
    return () => clearTimeout(timer);
  }, [noLeds, state, refreshState]);

  // Mirror the live effect so successive setters in the same tick chain off the
  // latest value instead of a stale render-time snapshot.
  const effectRef = useRef<EffectState | null>(null);
  useEffect(() => {
    if (state) effectRef.current = state.effect;
  }, [state]);

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

  const setChargerOnly = (chargerOnly: boolean) => {
    setState((s) => (s ? { ...s, chargerOnly } : s));
    api.setChargerOnly(chargerOnly).catch((e) =>
      console.error("Colores: setChargerOnly failed", e),
    );
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

  const pushGradientSpeed = useThrottle((v: number) => api.setGradientSpeed(v), 60);
  const setGradientSpeed = (gradientSpeed: number) => {
    setState((s) => (s ? { ...s, gradientSpeed } : s));
    pushGradientSpeed(gradientSpeed);
  };

  const updateEffect = (patch: Partial<EffectState>) => {
    const base = effectRef.current ?? { id: "breathing", speed: 50, useGradient: false };
    const next: EffectState = { ...base, ...patch };
    effectRef.current = next;
    setState((s) => (s ? { ...s, effect: next } : s));
    pushEffect(next.id, next.speed, next.useGradient);
  };

  const setEffectId = (id: EffectId) => updateEffect({ id });
  const setEffectSpeed = (speed: number) => updateEffect({ speed });
  const setEffectGradient = (useGradient: boolean) => updateEffect({ useGradient });

  const setAmbilight = (saturation: number, smoothing: number, fps: number) => {
    setState((s) => (s ? { ...s, ambilight: { ...s.ambilight, saturation, smoothing, fps } } : s));
    pushAmbilight(saturation, smoothing, fps);
  };

  const setAmbilightSampling = (sampling: string) => {
    setState((s) => (s ? { ...s, ambilight: { ...s.ambilight, sampling } } : s));
    api.setAmbilightSampling(sampling).catch((e) =>
      console.error("Colores: setAmbilightSampling failed", e),
    );
  };

  const saveGradient = (name: string, stops: RGB[]) => {
    api
      .saveGradient(name, stops.map((c) => [c.r, c.g, c.b]))
      .then((savedGradients) => setState((s) => (s ? { ...s, savedGradients } : s)))
      .catch((e) => console.error("Colores: saveGradient failed", e));
  };

  const deleteGradient = (name: string) => {
    setState((s) =>
      s ? { ...s, savedGradients: s.savedGradients.filter((g) => g.name !== name) } : s,
    );
    api
      .deleteGradient(name)
      .then((savedGradients) => setState((s) => (s ? { ...s, savedGradients } : s)))
      .catch((e) => console.error("Colores: deleteGradient failed", e));
  };

  const setPowerLed = (off: boolean) => {
    setState((s) => (s ? { ...s, powerLedOff: off } : s));
    api.setPowerLed(off).catch((e) => console.error("Colores: setPowerLed failed", e));
  };

  const setForceControl = (forceControl: boolean) => {
    setState((s) => (s ? { ...s, forceControl } : s));
    api.setForceControl(forceControl).catch((e) =>
      console.error("Colores: setForceControl failed", e),
    );
  };

  const pushIndicator = useThrottle((on: boolean, level: number) => api.setIndicator(on, level), 60);
  const setIndicator = (indicatorOn: boolean, indicatorLevel: number) => {
    setState((s) => (s ? { ...s, indicatorOn, indicatorLevel } : s));
    pushIndicator(indicatorOn, indicatorLevel);
  };

  const saveStartupColor = () =>
    api.saveStartupColor().catch((e) => console.error("Colores: saveStartupColor failed", e));

  const setBatteryBreathe = (batteryBreathe: boolean) => {
    setState((s) => (s ? { ...s, batteryBreathe } : s));
    api.setBatteryBreathe(batteryBreathe).catch((e) =>
      console.error("Colores: setBatteryBreathe failed", e),
    );
  };

  const setTemperatureBreathe = (temperatureBreathe: boolean) => {
    setState((s) => (s ? { ...s, temperatureBreathe } : s));
    api.setTemperatureBreathe(temperatureBreathe).catch((e) =>
      console.error("Colores: setTemperatureBreathe failed", e),
    );
  };

  const setExperiment = (feature: string, on: boolean) => {
    api
      .setExperiment(feature, on)
      .then(refreshState)
      .catch((e) => console.error("Colores: setExperiment failed", e));
  };

  const reconnect = () =>
    api
      .reconnect()
      .then(refreshState)
      .catch((e) => console.error("Colores: reconnect failed", e));

  return {
    state,
    loadError,
    retry: refreshState,
    setBrightness,
    setPower,
    setChargerOnly,
    setMode,
    setColor,
    setGradient,
    setGradientSpeed,
    setEffectId,
    setEffectSpeed,
    setEffectGradient,
    setAmbilight,
    setAmbilightSampling,
    saveGradient,
    deleteGradient,
    setExperiment,
    setPowerLed,
    setForceControl,
    setIndicator,
    saveStartupColor,
    setBatteryBreathe,
    setTemperatureBreathe,
    reconnect,
  };
}
