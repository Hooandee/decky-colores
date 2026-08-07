import { useCallback, useEffect, useRef, useState } from "react";
import {
  ColoresState,
  EffectId,
  EffectState,
  Mode,
  ProfileScope,
  ProfileState,
  RGB,
  SensorBand,
  SensorKind,
} from "./types";
import * as api from "./api";
import { useRunningApp } from "./apps/useRunningApp";
import { nextProfileScope } from "./profiles/scope";

function withProfile(state: ColoresState, profileState: ProfileState): ColoresState {
  return {
    ...state,
    ...profileState.profile,
    profileContext: profileState,
  };
}

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
  const [profileScope, setProfileScope] = useState<ProfileScope>("global");
  const runningApp = useRunningApp();
  const initializedScope = useRef(false);
  const profileTarget = useRef<{ scope: ProfileScope; appKey: string | null }>({
    scope: "global",
    appKey: null,
  });

  const refreshState = useCallback(() => {
    api.getState()
      .then((s) => {
        setState(s);
        if (!initializedScope.current) {
          const scope = s.profileContext.scope;
          setProfileScope(scope);
          profileTarget.current = {
            scope,
            appKey: scope === "game" ? s.profileContext.appKey : null,
          };
          initializedScope.current = true;
        }
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

  const loadProfile = useCallback((scope: ProfileScope, appKey: string | null) => {
    profileTarget.current = { scope, appKey };
    setProfileScope(scope);
    return api
      .getProfileState(scope, appKey)
      .then((profileState) => setState((current) => (current ? withProfile(current, profileState) : current)))
      .catch((error) => console.error("Colores: getProfileState failed", error));
  }, []);

  useEffect(() => {
    if (!initializedScope.current) return;
    const next = nextProfileScope(profileScope, runningApp);
    if (next !== profileScope) {
      void loadProfile("global", null);
      return;
    }
    if (
      profileScope === "game" &&
      runningApp &&
      profileTarget.current.appKey !== runningApp.key
    ) {
      void loadProfile("game", runningApp.key);
    }
  }, [loadProfile, profileScope, runningApp]);

  const selectScope = (scope: ProfileScope) => {
    const appKey = scope === "game" ? runningApp?.key ?? null : null;
    if (scope === "game" && appKey === null) return;
    void loadProfile(scope, appKey);
  };

  const pushProfile = useCallback((changes: Record<string, unknown>) => {
    const target = profileTarget.current;
    return api
      .patchProfile(target.scope, target.appKey, changes)
      .then((profileState) =>
        setState((current) =>
          current ? { ...current, profileContext: profileState } : current,
        ),
      )
      .catch((error) => console.error("Colores: patchProfile failed", error));
  }, []);

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

  const effectRef = useRef<EffectState | null>(null);
  useEffect(() => {
    if (state) effectRef.current = state.effect;
  }, [state]);

  const pushSolid = useThrottle((c: RGB) => pushProfile({ color: [c.r, c.g, c.b] }), 60);
  const pushBrightness = useThrottle((v: number) => pushProfile({ brightness: v }), 60);
  const pushEffect = useThrottle(
    (id: EffectId, speed: number, useGradient: boolean) =>
      pushProfile({ effect: { id, speed, use_gradient: useGradient } }),
    60,
  );
  const pushAmbilight = useThrottle(
    (vividness: number, sm: number, fps: number) =>
      pushProfile({ ambilight: { vividness, smoothing: sm, fps } }),
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
    void pushProfile({ mode });
  };

  const setColor = (color: RGB) => {
    setState((s) => (s ? { ...s, color } : s));
    pushSolid(color);
  };

  const setGradient = (gradient: RGB[]) => {
    setState((s) => (s ? { ...s, gradient } : s));
    void pushProfile({ gradient: gradient.map((c) => [c.r, c.g, c.b]) });
  };

  const pushGradientSpeed = useThrottle((v: number) => pushProfile({ gradient_speed: v }), 60);
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

  const setAmbilight = (vividness: number, smoothing: number, fps: number) => {
    setState((s) => (s ? { ...s, ambilight: { ...s.ambilight, vividness, smoothing, fps } } : s));
    pushAmbilight(vividness, smoothing, fps);
  };

  const setAmbilightSampling = (sampling: string) => {
    setState((s) => (s ? { ...s, ambilight: { ...s.ambilight, sampling } } : s));
    void pushProfile({ ambilight: { sampling } });
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

  const setRememberStartup = (rememberStartup: boolean) => {
    setState((s) => (s ? { ...s, rememberStartup } : s));
    api.setRememberStartup(rememberStartup).catch((e) =>
      console.error("Colores: setRememberStartup failed", e),
    );
  };

  const setBatteryBreathe = (batteryBreathe: boolean) => {
    setState((s) => (s ? { ...s, batteryBreathe } : s));
    void pushProfile({ battery_breathe: batteryBreathe });
  };

  const setTemperatureBreathe = (temperatureBreathe: boolean) => {
    setState((s) => (s ? { ...s, temperatureBreathe } : s));
    void pushProfile({ temperature_breathe: temperatureBreathe });
  };

  const setFollowGlobal = (follow: boolean) => {
    if (!runningApp) return;
    api
      .setProfileFollowGlobal(runningApp.key, follow)
      .then(() => loadProfile("game", runningApp.key))
      .catch((error) => console.error("Colores: setProfileFollowGlobal failed", error));
  };

  const forgetGameProfile = () => {
    if (!runningApp) return;
    api
      .forgetProfile(runningApp.key)
      .then(() => loadProfile("game", runningApp.key))
      .catch((error) => console.error("Colores: forgetProfile failed", error));
  };

  const setSensorBands = (
    sensor: SensorKind,
    bands: SensorBand[],
  ) =>
    api.setSensorBands(sensor, bands).then((saved) => {
      setState((state) =>
        state
          ? { ...state, sensorBands: { ...state.sensorBands, [sensor]: saved } }
          : state,
      );
      return saved;
    });

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
    runningApp,
    profileScope,
    selectScope,
    setFollowGlobal,
    forgetGameProfile,
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
    setRememberStartup,
    setBatteryBreathe,
    setTemperatureBreathe,
    setSensorBands,
    reconnect,
  };
}
