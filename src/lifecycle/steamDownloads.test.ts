import { describe, expect, it, vi } from "vitest";

import { isSteamDownloading, watchSteamDownloads } from "./steamDownloads";

type Overview = { paused: boolean; update_state: string; update_is_upload: boolean };

function fakeDownloads() {
  let callback: ((overview: Overview) => void) | undefined;
  const unregister = vi.fn();
  return {
    downloads: {
      RegisterForDownloadOverview(handler: (overview: Overview) => void) {
        callback = handler;
        return { unregister };
      },
    },
    emit(overview: Partial<Overview>) {
      callback?.({ paused: false, update_state: "None", update_is_upload: false, ...overview });
    },
    unregister,
  };
}

describe("isSteamDownloading", () => {
  it("counts only running downloads", () => {
    expect(isSteamDownloading({ update_state: "Updating" })).toBe(true);
    expect(isSteamDownloading({ update_state: "Starting" })).toBe(true);
    expect(isSteamDownloading({ update_state: "Updating", paused: true })).toBe(false);
    expect(isSteamDownloading({ update_state: "Updating", update_is_upload: true })).toBe(false);
    expect(isSteamDownloading({ update_state: "None" })).toBe(false);
    expect(isSteamDownloading({ update_state: "Stopping" })).toBe(false);
  });
});

describe("watchSteamDownloads", () => {
  it("reports only changes so progress ticks never spam the backend", () => {
    const steam = fakeDownloads();
    const onChange = vi.fn(async (_active: boolean) => undefined);

    const stop = watchSteamDownloads(onChange, steam.downloads as never);
    steam.emit({ update_state: "None" });
    steam.emit({ update_state: "Updating" });
    steam.emit({ update_state: "Updating" });
    steam.emit({ update_state: "Updating", paused: true });
    steam.emit({ update_state: "None" });

    expect(onChange.mock.calls.map(([active]) => active)).toEqual([false, true, false]);
    stop();
    expect(steam.unregister).toHaveBeenCalled();
  });

  it("hands the bar back when unloaded in the middle of a download", () => {
    const steam = fakeDownloads();
    const onChange = vi.fn(async (_active: boolean) => undefined);

    const stop = watchSteamDownloads(onChange, steam.downloads as never);
    steam.emit({ update_state: "Updating" });
    stop();

    expect(onChange.mock.calls.map(([active]) => active)).toEqual([true, false]);
  });

  it("retries a state the backend did not receive", async () => {
    const steam = fakeDownloads();
    const onChange = vi
      .fn(async (_active: boolean) => undefined)
      .mockRejectedValueOnce(new Error("socket closed"));

    watchSteamDownloads(onChange, steam.downloads as never);
    steam.emit({ update_state: "Updating" });
    await Promise.resolve();
    await Promise.resolve();
    steam.emit({ update_state: "Updating" });

    expect(onChange.mock.calls.map(([active]) => active)).toEqual([true, true]);
  });

  it("is a no-op where Steam exposes no download overview", () => {
    expect(() => watchSteamDownloads(vi.fn(async () => undefined), {})()).not.toThrow();
  });
});
