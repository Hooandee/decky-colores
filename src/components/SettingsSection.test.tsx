import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import { Capabilities } from "../types";
import { SettingsSection } from "./SettingsSection";

vi.mock("@decky/ui", () => ({
  ButtonItem: ({ children }: { children: React.ReactNode }) => <button>{children}</button>,
  Focusable: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
  Navigation: { NavigateToExternalWeb: vi.fn() },
  PanelSectionRow: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  ToggleField: ({ label }: { label: string }) => <div>{label}</div>,
}));

vi.mock("../api", () => ({ getVersion: vi.fn() }));
vi.mock("../i18n", () => ({
  LanguageSelector: () => null,
  useI18n: () => ({ t: (key: string) => key }),
}));
vi.mock("../updater/UpdatePanel", () => ({ UpdatePanel: () => null }));
vi.mock("./CustomizeModal", () => ({ openCustomizeModal: vi.fn() }));
vi.mock("./ReportModal", () => ({ openReportModal: vi.fn() }));

const capabilities = {
  color: true,
  brightness: true,
  effects: true,
  ambilight: true,
  zones: 2,
  maxBrightness: 100,
  layout: [],
  perZone: false,
  hardwareEffects: true,
  reconnectable: true,
  perControllerColor: false,
  gradientCrossfade: true,
  supportedEffects: [],
  experimental: [],
  states: {},
  enabledExperiments: [],
  powerLed: true,
  powerLedSeparateStates: true,
  sleepChargingIndicator: false,
  hasBattery: true,
  batteryMode: true,
  temperatureMode: true,
  performanceMode: true,
  clockMode: true,
  audioMode: true,
  conflictsWithSystemRgb: false,
  persistentStartup: false,
  layoutKind: "rings",
} as Capabilities;

const renderSettings = (caps: Capabilities) =>
  renderToStaticMarkup(
    <SettingsSection
      caps={caps}
      device={{ name: "Test", board: "TEST", product: "Test" }}
      availableTabIds={[]}
      lang="es"
      forceControl={false}
      powerLedOff={false}
      powerLedAwakeOff={false}
      powerLedSuspendOff={true}
      sleepChargingIndicator={false}
      onForceControl={() => {}}
      onPowerLed={() => {}}
      onPowerLedState={() => {}}
      onSleepChargingIndicator={() => {}}
      onExperiment={() => {}}
      onReconnect={() => {}}
    />,
  );

describe("SettingsSection power LED", () => {
  it("shows separate awake and suspend controls for independent hardware", () => {
    const html = renderSettings(capabilities);

    expect(html).toContain("powerLed.awakeLabel");
    expect(html).toContain("powerLed.suspendLabel");
    expect(html).not.toContain("powerLed.label");
  });

  it("keeps one control for hardware with a shared power LED bit", () => {
    const html = renderSettings({
      ...capabilities,
      powerLedSeparateStates: false,
    });

    expect(html).toContain("powerLed.label");
    expect(html).not.toContain("powerLed.awakeLabel");
    expect(html).not.toContain("powerLed.suspendLabel");
  });
});

describe("SettingsSection sleep charging indicator", () => {
  it("shows the option only when the probed Aura interface supports it", () => {
    const supported = renderSettings({
      ...capabilities,
      sleepChargingIndicator: true,
    });
    const unsupported = renderSettings(capabilities);

    expect(supported).toContain("sleepCharging.label");
    expect(unsupported).not.toContain("sleepCharging.label");
  });
});
