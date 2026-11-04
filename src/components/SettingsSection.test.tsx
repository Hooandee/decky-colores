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
vi.mock("./StylesModal", () => ({ openStylesModal: vi.fn() }));
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


const styleActions = {
  save: vi.fn(),
  apply: vi.fn(),
  remove: vi.fn(),
  exportAll: vi.fn(),
  importFrom: vi.fn(),
};

const renderSettings = (caps: Capabilities, withStyles = false) =>
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
      colorCalibration={{ yellow: 0, orange: 0, white: 0 }}
      onColorCalibration={() => {}}
      styleActions={withStyles ? styleActions : undefined}
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

describe("SettingsSection color calibration", () => {
  it("offers calibration only when the LEDs take color", () => {
    expect(renderSettings({ ...capabilities, color: true })).toContain("calibration.button");
    expect(renderSettings({ ...capabilities, color: false })).not.toContain("calibration.button");
  });
});

describe("SettingsSection saved presets", () => {
  it("lists saved presets in settings when the LEDs take color", () => {
    expect(renderSettings({ ...capabilities, color: true }, true)).toContain("styles.button");
    expect(renderSettings({ ...capabilities, color: false }, true)).not.toContain("styles.button");
  });
});
