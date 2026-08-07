import { setCurrentApp } from "../api";
import { shouldReportApp } from "./gameReport";
import { readRunningApp } from "./runningApp";

const POLL_MS = 2000;
const STARTUP_POLLS = 30;

export function startGameWatcher(): () => void {
  let alive = true;
  let committed: string | null = null;
  let inFlight: string | null | undefined;
  let reportedGame = false;
  let startupTicks = 0;
  let startupTimer: ReturnType<typeof setInterval> | null = null;
  let fallbackTimer: ReturnType<typeof setInterval> | null = null;
  let retryTimer: ReturnType<typeof setTimeout> | null = null;
  let unregister: (() => void) | null = null;

  const stopStartup = () => {
    if (startupTimer !== null) clearInterval(startupTimer);
    startupTimer = null;
  };

  const retry = () => {
    if (!alive || retryTimer !== null) return;
    retryTimer = setTimeout(() => {
      retryTimer = null;
      report();
    }, POLL_MS);
  };

  const report = () => {
    if (!alive) return;
    const target = readRunningApp()?.key ?? null;
    if (!shouldReportApp(target, committed, inFlight)) return;
    inFlight = target;
    try {
      Promise.resolve(setCurrentApp(target))
        .then(() => {
          if (!alive) return;
          committed = target;
          inFlight = undefined;
          if (target !== null) {
            reportedGame = true;
            stopStartup();
          }
          report();
        })
        .catch(() => {
          if (inFlight === target) inFlight = undefined;
          retry();
        });
    } catch {
      if (inFlight === target) inFlight = undefined;
      retry();
    }
  };

  report();
  startupTimer = setInterval(() => {
    startupTicks += 1;
    if (!alive || reportedGame || startupTicks >= STARTUP_POLLS) {
      stopStartup();
      return;
    }
    report();
  }, POLL_MS);

  try {
    const registration = SteamClient?.GameSessions?.RegisterForAppLifetimeNotifications?.(
      report,
    );
    if (registration && typeof registration.unregister === "function") {
      unregister = () => registration.unregister();
    } else {
      fallbackTimer = setInterval(report, POLL_MS);
    }
  } catch {
    fallbackTimer = setInterval(report, POLL_MS);
  }

  return () => {
    alive = false;
    stopStartup();
    if (fallbackTimer !== null) clearInterval(fallbackTimer);
    if (retryTimer !== null) clearTimeout(retryTimer);
    if (unregister) {
      try {
        unregister();
      } catch {
        return;
      }
    }
  };
}
