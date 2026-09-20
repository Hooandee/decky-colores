import { describe, expect, it, vi } from "vitest";

import { ProfileState } from "../types";
import { profileStateForApp, selectProfileScope } from "./scope";

const profile = {
  brightness: 55,
  mode: "solid" as const,
  color: { r: 10, g: 20, b: 30 },
  gradient: [{ r: 10, g: 20, b: 30 }],
  gradientSpeed: 40,
  effect: { id: "breathing" as const, speed: 50, useGradient: false },
  ambilight: { vividness: 50, smoothing: 50, fps: 30, sampling: "columns" },
  batteryBreathe: true,
  temperatureBreathe: true,
};

const globalState: ProfileState = {
  scope: "global",
  appKey: null,
  profile,
  hasGameProfile: false,
  followsGlobal: true,
  activeProfile: "default",
};

const gameState: ProfileState = {
  scope: "game",
  appKey: "42",
  profile: { ...profile, brightness: 80 },
  hasGameProfile: true,
  followsGlobal: false,
  activeProfile: "default",
};

type ProfileApi = {
  getProfileState: (scope: "global" | "game", appKey: string | null) => Promise<ProfileState>;
  setProfileFollowGlobal: (appKey: string, follow: boolean) => Promise<ProfileState>;
};

describe("profile scope selection", () => {
  it("activates and returns the running game's preserved profile for Juego", async () => {
    const api: ProfileApi = {
      getProfileState: vi.fn(async () => globalState),
      setProfileFollowGlobal: vi.fn(async () => gameState),
    };

    await expect(selectProfileScope("game", "42", api)).resolves.toEqual(gameState);
    expect(api.setProfileFollowGlobal).toHaveBeenCalledWith("42", false);
    expect(api.getProfileState).not.toHaveBeenCalled();
  });

  it("activates Global and loads its values without deleting the game profile", async () => {
    const calls: string[] = [];
    const api: ProfileApi = {
      getProfileState: vi.fn(async () => {
        calls.push("load-global");
        return globalState;
      }),
      setProfileFollowGlobal: vi.fn(async () => {
        calls.push("follow-global");
        return { ...gameState, followsGlobal: true };
      }),
    };

    await expect(selectProfileScope("global", "42", api)).resolves.toEqual(globalState);
    expect(api.setProfileFollowGlobal).toHaveBeenCalledWith("42", true);
    expect(api.getProfileState).toHaveBeenCalledWith("global", null);
    expect(calls).toEqual(["load-global", "follow-global"]);
  });

  it("resolves the selected tab from the running game's persisted inheritance", async () => {
    const ownApi = { getProfileState: vi.fn(async () => gameState) };
    await expect(profileStateForApp("42", ownApi)).resolves.toEqual(gameState);

    const followingApi = {
      getProfileState: vi.fn(async (scope: "global" | "game") =>
        scope === "game" ? { ...gameState, followsGlobal: true } : globalState),
    };
    await expect(profileStateForApp("42", followingApi)).resolves.toEqual(globalState);
    expect(followingApi.getProfileState).toHaveBeenNthCalledWith(1, "game", "42");
    expect(followingApi.getProfileState).toHaveBeenNthCalledWith(2, "global", null);
  });
});
