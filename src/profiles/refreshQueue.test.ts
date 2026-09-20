import { describe, expect, it, vi } from "vitest";

import { createRefreshQueue } from "./refreshQueue";

describe("profile refresh queue", () => {
  it("runs one queued getState after an active transition is accepted", async () => {
    const applied: string[] = [];
    const run = vi.fn(async () => {
      applied.push("B");
    });
    const queue = createRefreshQueue(run);
    queue.block();
    queue.request();
    queue.request();
    expect(run).not.toHaveBeenCalled();

    queue.release();
    await vi.waitFor(() => expect(run).toHaveBeenCalledTimes(1));
    expect(applied).toEqual(["B"]);
  });
});
