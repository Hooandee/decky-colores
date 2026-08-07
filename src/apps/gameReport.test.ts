import { describe, expect, it } from "vitest";

import { shouldReportApp } from "./gameReport";

describe("shouldReportApp", () => {
  it("retries an app key rejected before commit", () => {
    expect(shouldReportApp("42", null, undefined)).toBe(true);
  });

  it("reports exit while idle", () => {
    expect(shouldReportApp(null, "42", undefined)).toBe(true);
  });

  it("deduplicates committed and in-flight values", () => {
    expect(shouldReportApp("42", "42", undefined)).toBe(false);
    expect(shouldReportApp("42", null, "42")).toBe(false);
  });

  it("waits for the current request before reporting a different app", () => {
    expect(shouldReportApp("84", null, "42")).toBe(false);
  });
});
