import { describe, expect, it, vi } from "vitest";

import { ProfileState } from "../types";
import {
  createProfileTransitionRunner,
  createStateSnapshotGate,
  snapshotProfileTarget,
} from "./transition";

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

const stateFor = (appKey: string, brightness: number): ProfileState => ({
  scope: "game",
  appKey,
  profile: { ...profile, brightness },
  hasGameProfile: true,
  followsGlobal: false,
  activeProfile: "default",
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe("profile transition runner", () => {
  it("accepts only the latest app when A and B resolve out of order", async () => {
    const a = deferred<ProfileState>();
    const b = deferred<ProfileState>();
    const accepted: string[] = [];
    const runner = createProfileTransitionRunner(
      { getProfileState: vi.fn() },
      {
        onStart: vi.fn(),
        onAccept: (appKey) => accepted.push(appKey ?? "global"),
        onError: vi.fn(),
      },
    );
    const runA = runner.run("A", () => a.promise);
    const runB = runner.run("B", () => b.promise);
    a.resolve(stateFor("A", 40));
    await runA;
    expect(accepted).toEqual([]);

    b.resolve(stateFor("B", 80));
    await runB;
    expect(accepted).toEqual(["B"]);
  });

  it("reconciles the effective profile after a partial selection failure", async () => {
    const globalState: ProfileState = {
      ...stateFor("B", 25),
      scope: "global",
      appKey: null,
      followsGlobal: true,
    };
    const getProfileState = vi.fn(async (scope: "global" | "game") =>
      scope === "game"
        ? { ...stateFor("B", 80), followsGlobal: true }
        : globalState,
    );
    const onAccept = vi.fn();
    const onError = vi.fn();
    const runner = createProfileTransitionRunner(
      { getProfileState },
      { onStart: vi.fn(), onAccept, onError },
    );
    await runner.run("B", async () => {
      throw new Error("read after mutation failed");
    });

    expect(onAccept).toHaveBeenCalledWith("B", globalState);
    expect(onError).not.toHaveBeenCalled();
    expect(getProfileState).toHaveBeenNthCalledWith(1, "game", "B");
    expect(getProfileState).toHaveBeenNthCalledWith(2, "global", null);
  });

  it("keeps the transition blocked and exposes an error when reconciliation fails", async () => {
    const error = new Error("profile unavailable");
    const onError = vi.fn();
    const runner = createProfileTransitionRunner(
      { getProfileState: vi.fn(async () => { throw error; }) },
      { onStart: vi.fn(), onAccept: vi.fn(), onError },
    );
    await runner.run("B", async () => { throw error; });

    expect(runner.isActiveFor("B")).toBe(true);
    expect(onError).toHaveBeenCalledWith("B", error);
  });
});

describe("profile write targets", () => {
  it("captures the game target before a delayed write runs", () => {
    const current = { scope: "game" as const, appKey: "A" };
    const queued = snapshotProfileTarget(current);
    current.appKey = "B";

    expect(queued).toEqual({ scope: "game", appKey: "A" });
  });
});

describe("state snapshot gate", () => {
  it("rejects a getState snapshot started before a newer profile transition", () => {
    const gate = createStateSnapshotGate();
    const loadingA = gate.begin();
    gate.invalidate();
    gate.invalidate();

    expect(gate.isCurrent(loadingA)).toBe(false);
    const loadingB = gate.begin();
    expect(gate.isCurrent(loadingB)).toBe(true);
  });
});
