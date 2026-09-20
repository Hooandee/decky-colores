import { afterEach, describe, expect, it, vi } from "vitest";

import { createThrottle } from "./throttle";

afterEach(() => {
  vi.useRealTimers();
});

describe("createThrottle", () => {
  it("flushes and awaits the latest delayed write before a profile read", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000);
    const order: string[] = [];
    let releaseWrite!: () => void;
    const throttled = createThrottle(async (value: string) => {
      order.push(`write:${value}`);
      await new Promise<void>((resolve) => { releaseWrite = resolve; });
      order.push(`saved:${value}`);
    }, 60);

    throttled.run("first");
    releaseWrite();
    await Promise.resolve();
    throttled.run("latest");

    const flushed = throttled.flush().then(() => order.push("read"));
    expect(order).toContain("write:latest");
    expect(order).not.toContain("read");
    releaseWrite();
    await flushed;

    expect(order.slice(-2)).toEqual(["saved:latest", "read"]);
  });
});
