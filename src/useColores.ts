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
import {
  profileScopeFor,
  profileStateForApp,
  selectProfileScope as applyProfileScope,
} from "./profiles/scope";
import {
  createStateSnapshotGate,
  createProfileTransitionRunner,
  ProfileTarget,
  ProfileTransitionRunner,
  snapshotProfileTarget,
} from "./profiles/transition";
import { createRefreshQueue, RefreshQueue } from "./profiles/refreshQueue";
import { createThrottle, Throttled } from "./throttle";

function withProfile(state: ColoresState, profileState: ProfileState): ColoresState {
  return {
    ...state,
    ...profileState.profile,
    profileContext: profileState,
  };
}

function useThrottle<A extends unknown[]>(
  fn: (...args: A) => void | Promise<unknown>,
  ms: number,
): Throttled<A> {
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const throttled = useRef<Throttled<A> | null>(null);
  if (throttled.current === null) {
    throttled.current = createThrottle((...args: A) => fnRef.current(...args), ms);
  }
  useEffect(() => () => throttled.current?.cancel(), []);
  return throttled.current;
}

export function useColores() {
  const [state, setState] = useState<ColoresState | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [profileScope, setProfileScope] = useState<ProfileScope>("global");
  const runningApp = useRunningApp();
  const initializedScope = useRef(false);
  const [resolvedAppKey, setResolvedAppKey] = useState<string | null | undefined>(undefined);
  const [profileStatus, setProfileStatus] = useState<"idle" | "loading" | "error">("idle");
  const stateSnapshotGate = useRef(createStateSnapshotGate());
  const flushProfileWritesRef = useRef<() => Promise<void>>(async () => {});
  const profileTarget = useRef<ProfileTarget>({
    scope: "global",
    appKey: null,
  });

  const acceptStateSnapshot = useCallback((snapshot: ColoresState) => {
    const scope = profileScopeFor(
      snapshot.profileContext.appKey,
      snapshot.profileContext.followsGlobal,
    );
    profileTarget.current = {
      scope,
      appKey: scope === "game" ? snapshot.profileContext.appKey : null,
    };
    setState(snapshot);
    setProfileScope(scope);
    setResolvedAppKey(snapshot.profileContext.appKey);
    setProfileStatus("idle");
    initializedScope.current = true;
  }, []);

  const performRefresh = useCallback(async () => {
    const token = stateSnapshotGate.current.begin();
    await flushProfileWritesRef.current();
    if (!stateSnapshotGate.current.isCurrent(token)) return;
    try {
      const snapshot = await api.getState();
      if (!stateSnapshotGate.current.isCurrent(token)) return;
      acceptStateSnapshot(snapshot);
      setLoadError(false);
    } catch (e) {
      if (!stateSnapshotGate.current.isCurrent(token)) return;
      console.error("Colores: getState failed", e);
      setLoadError(true);
    }
  }, [acceptStateSnapshot]);

  const performRefreshRef = useRef(performRefresh);
  performRefreshRef.current = performRefresh;
  const refreshQueue = useRef<RefreshQueue | null>(null);
  if (refreshQueue.current === null) {
    refreshQueue.current = createRefreshQueue(() => performRefreshRef.current());
  }
  const refreshState = useCallback(() => refreshQueue.current?.request(), []);

  useEffect(() => {
    refreshState();
  }, [refreshState]);

  const acceptProfileState = useCallback((appKey: string | null, profileState: ProfileState) => {
    profileTarget.current = {
      scope: profileState.scope,
      appKey: profileState.scope === "game" ? profileState.appKey : null,
    };
    setProfileScope(profileState.scope);
    setResolvedAppKey(appKey);
    setProfileStatus("idle");
    setState((current) => (current ? withProfile(current, profileState) : current));
  }, []);

  const acceptProfileStateRef = useRef(acceptProfileState);
  acceptProfileStateRef.current = acceptProfileState;
  const transitionRunner = useRef<ProfileTransitionRunner | null>(null);
  if (transitionRunner.current === null) {
    transitionRunner.current = createProfileTransitionRunner(api, {
      onStart: () => {
        refreshQueue.current?.block();
        stateSnapshotGate.current.invalidate();
        setProfileStatus("loading");
      },
      onAccept: (appKey, profileState) => {
        stateSnapshotGate.current.invalidate();
        acceptProfileStateRef.current(appKey, profileState);
        refreshQueue.current?.release();
      },
      onError: (_appKey, error) => {
        stateSnapshotGate.current.invalidate();
        console.error("Colores: profile transition failed", error);
        setProfileStatus("error");
      },
    });
  }

  const runningAppKey = runningApp?.key ?? null;
  const profilePending =
    state !== null && (profileStatus !== "idle" || resolvedAppKey !== runningAppKey);

  useEffect(() => {
    if (!initializedScope.current) return;
    const appKey = runningApp?.key ?? null;
    if (resolvedAppKey === appKey && profileStatus === "idle") return;
    if (transitionRunner.current?.isActiveFor(appKey)) return;
    void transitionRunner.current?.run(appKey, async () => {
      await flushProfileWritesRef.current();
      return profileStateForApp(appKey, api);
    });
  }, [profileStatus, resolvedAppKey, runningApp]);

  const selectScope = (scope: ProfileScope) => {
    const appKey = runningAppKey;
    if (profilePending) return;
    if (scope === "game" && appKey === null) return;
    if (scope === profileScope) return;
    void transitionRunner.current?.run(appKey, async () => {
      await flushProfileWritesRef.current();
      const profileState = await applyProfileScope(scope, appKey, api);
      if (!profileState) throw new Error("Invalid profile scope for the current app");
      return profileState;
    });
  };

  const retryProfile = () => {
    const appKey = runningAppKey;
    void transitionRunner.current?.run(appKey, async () => {
      await flushProfileWritesRef.current();
      return profileStateForApp(appKey, api);
    });
  };

  const profileWriteQueue = useRef<Promise<void>>(Promise.resolve());
  const pushProfile = useCallback((target: ProfileTarget, changes: Record<string, unknown>) => {
    const request = profileWriteQueue.current.then(() =>
      api.patchProfile(target.scope, target.appKey, changes),
    );
    const settled = request
      .then((profileState) => {
        const visible = profileTarget.current;
        if (visible.scope === target.scope && visible.appKey === target.appKey) {
          setState((current) =>
            current ? withProfile(current, profileState) : current,
          );
        }
      })
      .catch((error) => console.error("Colores: patchProfile failed", error));
    profileWriteQueue.current = settled;
    return settled;
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

  const pushSolid = useThrottle(
    (target: ProfileTarget, c: RGB) => pushProfile(target, { color: [c.r, c.g, c.b] }),
    60,
  );
  const pushBrightness = useThrottle(
    (target: ProfileTarget, v: number) => pushProfile(target, { brightness: v }),
    60,
  );
  const pushEffect = useThrottle(
    (target: ProfileTarget, id: EffectId, speed: number, useGradient: boolean) =>
      pushProfile(target, { effect: { id, speed, use_gradient: useGradient } }),
    60,
  );
  const pushAmbilight = useThrottle(
    (target: ProfileTarget, vividness: number, sm: number, fps: number) =>
      pushProfile(target, { ambilight: { vividness, smoothing: sm, fps } }),
    80,
  );
  const pushGradientSpeed = useThrottle(
    (target: ProfileTarget, v: number) => pushProfile(target, { gradient_speed: v }),
    60,
  );
  flushProfileWritesRef.current = async () => {
    await Promise.all([
      pushSolid.flush(),
      pushBrightness.flush(),
      pushEffect.flush(),
      pushAmbilight.flush(),
      pushGradientSpeed.flush(),
    ]);
    await profileWriteQueue.current;
  };

  const setBrightness = (brightness: number) => {
    setState((s) => (s ? { ...s, brightness } : s));
    pushBrightness.run(snapshotProfileTarget(profileTarget.current), brightness);
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
    void pushProfile(snapshotProfileTarget(profileTarget.current), { mode });
  };

  const setColor = (color: RGB) => {
    setState((s) => (s ? { ...s, color } : s));
    pushSolid.run(snapshotProfileTarget(profileTarget.current), color);
  };

  const setGradient = (gradient: RGB[]) => {
    setState((s) => (s ? { ...s, gradient } : s));
    void pushProfile(snapshotProfileTarget(profileTarget.current), {
      gradient: gradient.map((c) => [c.r, c.g, c.b]),
    });
  };

  const setGradientSpeed = (gradientSpeed: number) => {
    setState((s) => (s ? { ...s, gradientSpeed } : s));
    pushGradientSpeed.run(snapshotProfileTarget(profileTarget.current), gradientSpeed);
  };

  const updateEffect = (patch: Partial<EffectState>) => {
    const base = effectRef.current ?? { id: "breathing", speed: 50, useGradient: false };
    const next: EffectState = { ...base, ...patch };
    effectRef.current = next;
    setState((s) => (s ? { ...s, effect: next } : s));
    pushEffect.run(
      snapshotProfileTarget(profileTarget.current),
      next.id,
      next.speed,
      next.useGradient,
    );
  };

  const setEffectId = (id: EffectId) => updateEffect({ id });
  const setEffectSpeed = (speed: number) => updateEffect({ speed });
  const setEffectGradient = (useGradient: boolean) => updateEffect({ useGradient });

  const setAmbilight = (vividness: number, smoothing: number, fps: number) => {
    setState((s) => (s ? { ...s, ambilight: { ...s.ambilight, vividness, smoothing, fps } } : s));
    pushAmbilight.run(
      snapshotProfileTarget(profileTarget.current),
      vividness,
      smoothing,
      fps,
    );
  };

  const setAmbilightSampling = (sampling: string) => {
    setState((s) => (s ? { ...s, ambilight: { ...s.ambilight, sampling } } : s));
    void pushProfile(snapshotProfileTarget(profileTarget.current), {
      ambilight: { sampling },
    });
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

  const setPowerLedState = (powerLedState: "awake" | "suspend", off: boolean) => {
    const key = powerLedState === "awake" ? "powerLedAwakeOff" : "powerLedSuspendOff";
    setState((s) => (s ? { ...s, [key]: off } : s));
    api
      .setPowerLedState(powerLedState, off)
      .catch((e) => console.error("Colores: setPowerLedState failed", e));
  };

  const setSleepChargingIndicator = (sleepChargingIndicator: boolean) => {
    setState((s) => (s ? { ...s, sleepChargingIndicator } : s));
    api.setSleepChargingIndicator(sleepChargingIndicator).catch((e) =>
      console.error("Colores: setSleepChargingIndicator failed", e),
    );
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
    void pushProfile(snapshotProfileTarget(profileTarget.current), {
      battery_breathe: batteryBreathe,
    });
  };

  const setTemperatureBreathe = (temperatureBreathe: boolean) => {
    setState((s) => (s ? { ...s, temperatureBreathe } : s));
    void pushProfile(snapshotProfileTarget(profileTarget.current), {
      temperature_breathe: temperatureBreathe,
    });
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
    profilePending,
    profileError: profileStatus === "error",
    selectScope,
    retryProfile,
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
    setPowerLedState,
    setSleepChargingIndicator,
    setForceControl,
    setRememberStartup,
    setBatteryBreathe,
    setTemperatureBreathe,
    setSensorBands,
    reconnect,
  };
}
