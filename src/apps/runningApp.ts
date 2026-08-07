import { Router } from "@decky/ui";

import { GameOverview, stableGameKey } from "./gameIdentity";

export interface RunningApp {
  key: string;
  liveAppId: number;
  name: string;
}

export function readRunningApp(): RunningApp | null {
  try {
    const router = Router as unknown as { MainRunningApp?: GameOverview };
    const app = router?.MainRunningApp;
    if (!app?.appid) return null;
    return {
      key: stableGameKey(app),
      liveAppId: Number(app.appid),
      name: app.display_name || String(app.appid),
    };
  } catch {
    return null;
  }
}
