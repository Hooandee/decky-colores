import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import { Capabilities } from "../types";
import { SettingsSection } from "./SettingsSection";

vi.mock("@decky/ui", () => ({
  ButtonItem: ({ children }: { children: React.ReactNode }) => <button>{children}</button>,
  Dropdown: ({ menuLabel }: { menuLabel: string }) => <div>{menuLabel}</div>,
  Focusable: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
  Navigation: { NavigateToExternalWeb: vi.fn() },
  PanelSectionRow: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  SliderField: ({ label }: { label: string }) => <div>{label}</div>,
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

const renderSettings = (caps: Capabilities, ssdActivityLed = false) =>
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
      ssdActivityLed={ssdActivityLed}
      ssdActivityDirection="both"
      ssdActivitySensitivity={50}
      sleepChargingIndicator={false}
      onForceControl={() => {}}
      onPowerLed={() => {}}
      onPowerLedState={() => {}}
      onSsdActivityLed={() => {}}
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

  it("shows activity controls beneath the power LED setting when enabled", () => {
    const enabled = renderSettings(capabilities, true);
    const disabled = renderSettings(capabilities);
    expect(enabled).toContain("powerLed.ssdActivity");
    expect(enabled).toContain("powerLed.ssdDirection");
    expect(enabled).toContain("powerLed.ssdSensitivity");
    expect(disabled).not.toContain("powerLed.ssdSensitivity");
    expect(renderSettings({ ...capabilities, powerLed: false })).not.toContain("powerLed.ssdActivity");
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
