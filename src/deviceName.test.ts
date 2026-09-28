import { describe, expect, it } from "vitest";

import { deviceDisplayName } from "./deviceName";

describe("deviceDisplayName", () => {
  it("translates the Armada OS fallback", () => {
    const name = deviceDisplayName(
      { name: "Armada OS Device", board: "", product: "", displayNameKey: "device.armadaOs" },
      (key) => (key === "device.armadaOs" ? "Dispositivo Armada OS" : key),
    );

    expect(name).toBe("Dispositivo Armada OS");
  });

  it("keeps a hardware-provided name", () => {
    const name = deviceDisplayName(
      { name: "AYN Odin 2", board: "", product: "" },
      (key) => key,
    );

    expect(name).toBe("AYN Odin 2");
  });
});
