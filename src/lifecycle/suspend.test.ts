import { describe, expect, it, vi } from "vitest";

import { startSuspendPreparation } from "./suspend";

type Progress = { state: number; bGameSuspended: boolean };

function fakeSteamUser() {
  let callback: ((progress: Progress) => void) | undefined;
  const unregister = vi.fn();
  return {
    user: {
      RegisterForPrepareForSystemSuspendProgress(handler: (progress: Progress) => void) {
        callback = handler;
        return { unregister };
      },
    },
    progress(value: Progress) {
      callback?.(value);
    },
    unregister,
  };
}

describe("startSuspendPreparation", () => {
  it("only prepares Colores when Steam finishes its pre-suspend work", () => {
    const steam = fakeSteamUser();
    const prepare = vi.fn(async () => undefined);

    startSuspendPreparation(prepare, steam.user);
    steam.progress({ state: 0, bGameSuspended: false });
    expect(prepare).not.toHaveBeenCalled();
    steam.progress({ state: 1, bGameSuspended: true });
    expect(prepare).toHaveBeenCalledOnce();
  });

  it("unregisters the Steam callback when the plugin dismounts", () => {
    const steam = fakeSteamUser();
    const prepare = vi.fn(async () => undefined);
    const stop = startSuspendPreparation(prepare, steam.user);

    stop();

    expect(steam.unregister).toHaveBeenCalledOnce();
  });
});
