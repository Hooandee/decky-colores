import { useEffect, useState } from "react";

import { readRunningApp, RunningApp } from "./runningApp";

export function useRunningApp(): RunningApp | null {
  const [app, setApp] = useState<RunningApp | null>(() => readRunningApp());

  useEffect(() => {
    let alive = true;
    let unregister: (() => void) | null = null;
    let timer: ReturnType<typeof setInterval> | null = null;
    const sync = () => {
      if (!alive) return;
      const next = readRunningApp();
      setApp((previous) =>
        previous?.key === next?.key &&
        previous?.liveAppId === next?.liveAppId &&
        previous?.name === next?.name
          ? previous
          : next,
      );
    };

    try {
      const registration =
        SteamClient?.GameSessions?.RegisterForAppLifetimeNotifications?.(sync);
      if (registration && typeof registration.unregister === "function") {
        unregister = () => registration.unregister();
      } else {
        timer = setInterval(sync, 2000);
      }
    } catch {
      timer = setInterval(sync, 2000);
    }

    return () => {
      alive = false;
      if (timer !== null) clearInterval(timer);
      if (unregister) unregister();
    };
  }, []);

  return app;
}
