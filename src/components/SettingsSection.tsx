import { FC, Fragment, ReactNode, useEffect, useState } from "react";
import { ButtonItem, Dropdown, Focusable, Navigation, PanelSectionRow, SliderField, ToggleField } from "@decky/ui";

import { getVersion } from "../api";
import { useI18n, LanguageSelector, type Lang } from "../i18n";
import { UpdatePanel } from "../updater/UpdatePanel";
import { openCustomizeModal } from "./CustomizeModal";
import { openReportModal } from "./ReportModal";
import { Divider } from "./Divider";
import { Capabilities, DeviceInfo, PowerLedState, SsdActivityDirection } from "../types";

const AUTHOR = "Hooandee";
const YOUTUBE_URL = "https://www.youtube.com/@Hooandee";

let versionCache = "";

const sectionTitle = (text: string) => (
  <PanelSectionRow>
    <div style={{ fontWeight: 600, fontSize: 13, padding: "2px 2px 6px" }}>{text}</div>
  </PanelSectionRow>
);

const hint = (text: string) => (
  <PanelSectionRow>
    <div style={{ fontSize: 12, color: "rgba(255,255,255,0.55)", padding: "0 2px 8px", lineHeight: 1.45 }}>
      {text}
    </div>
  </PanelSectionRow>
);

interface SettingsSectionProps {
  caps: Capabilities;
  device: DeviceInfo;
  availableTabIds: string[];
  lang: Lang;
  forceControl: boolean;
  powerLedOff: boolean;
  powerLedAwakeOff: boolean;
  powerLedSuspendOff: boolean;
  ssdActivityLed: boolean;
  ssdActivityDirection: SsdActivityDirection;
  ssdActivitySensitivity: number;
  sleepChargingIndicator: boolean;
  onForceControl: (v: boolean) => void;
  onPowerLed: (v: boolean) => void;
  onPowerLedState: (state: PowerLedState, value: boolean) => void;
  onSsdActivityLed: (enabled: boolean, direction: SsdActivityDirection, sensitivity: number) => void;
  onSleepChargingIndicator: (value: boolean) => void;
  onExperiment: (feature: string, val: boolean) => void;
  onReconnect: () => void;
}

export const SettingsSection: FC<SettingsSectionProps> = ({
  caps,
  device,
  availableTabIds,
  lang,
  forceControl,
  powerLedOff,
  powerLedAwakeOff,
  powerLedSuspendOff,
  ssdActivityLed,
  ssdActivityDirection,
  ssdActivitySensitivity,
  sleepChargingIndicator,
  onForceControl,
  onPowerLed,
  onPowerLedState,
  onSsdActivityLed,
  onSleepChargingIndicator,
  onExperiment,
  onReconnect,
}) => {
  const { t } = useI18n();
  const [version, setVersion] = useState(versionCache);
  useEffect(() => {
    if (versionCache) return;
    let alive = true;
    getVersion()
      .then((v) => {
        versionCache = v;
        if (alive) setVersion(v);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  const openChannel = () => Navigation.NavigateToExternalWeb(YOUTUBE_URL);
  const [madeByBefore, madeByAfter] = t("about.madeBy").split("{name}");
  const unvalidated = !(caps.color || caps.brightness);

  const sections: ReactNode[] = [
    <PanelSectionRow>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 8,
          padding: "2px 2px",
          marginTop: 12,
        }}
      >
        <span style={{ fontSize: 13, color: "rgba(255,255,255,0.9)" }}>{t("settings.language")}</span>
        <LanguageSelector />
      </div>
    </PanelSectionRow>,

    caps.conflictsWithSystemRgb ? (
      <>
        <PanelSectionRow>
          <ToggleField
            label={t("forceControl.label")}
            description={t("forceControl.hint")}
            checked={forceControl}
            onChange={onForceControl}
            bottomSeparator="none"
          />
        </PanelSectionRow>
        {hint(t("forceControl.notice"))}
      </>
    ) : null,

    caps.reconnectable ? (
      <>
        <PanelSectionRow>
          <ButtonItem layout="below" bottomSeparator="none" onClick={onReconnect}>
            {t("reconnect.label")}
          </ButtonItem>
        </PanelSectionRow>
        {hint(t("reconnect.hint"))}
      </>
    ) : null,

    caps.powerLed ? (
      <>
        {sectionTitle(t("powerLed.section"))}
        {caps.powerLedSeparateStates ? (
          <>
            <PanelSectionRow>
              <ToggleField
                label={t("powerLed.awakeLabel")}
                checked={powerLedAwakeOff}
                onChange={(value) => onPowerLedState("awake", value)}
                bottomSeparator="none"
              />
            </PanelSectionRow>
            <PanelSectionRow>
              <ToggleField
                label={t("powerLed.suspendLabel")}
                checked={powerLedSuspendOff}
                onChange={(value) => onPowerLedState("suspend", value)}
                bottomSeparator="none"
              />
            </PanelSectionRow>
            {hint(t("powerLed.separateWarning"))}
          </>
        ) : (
          <>
            <PanelSectionRow>
              <ToggleField label={t("powerLed.label")} checked={powerLedOff} onChange={onPowerLed} bottomSeparator="none" />
            </PanelSectionRow>
            {hint(t("powerLed.warning"))}
          </>
        )}
        <PanelSectionRow>
          <ToggleField
            label={t("powerLed.ssdActivity")}
            description={t("powerLed.ssdHint")}
            checked={ssdActivityLed}
            onChange={(enabled) => onSsdActivityLed(enabled, ssdActivityDirection, ssdActivitySensitivity)}
            bottomSeparator="none"
          />
        </PanelSectionRow>
        {ssdActivityLed && (
          <>
            <PanelSectionRow>
              <Dropdown
                rgOptions={(["read", "write", "both"] as const).map((direction) => ({
                  data: direction, label: t(`powerLed.ssd.${direction}`),
                }))}
                selectedOption={ssdActivityDirection}
                menuLabel={t("powerLed.ssdDirection")}
                onChange={(option) => onSsdActivityLed(true, option.data, ssdActivitySensitivity)}
              />
            </PanelSectionRow>
            <PanelSectionRow>
              <SliderField
                label={t("powerLed.ssdSensitivity")}
                value={ssdActivitySensitivity}
                min={0}
                max={100}
                step={1}
                valueSuffix="%"
                showValue
                onChange={(value) => onSsdActivityLed(true, ssdActivityDirection, value)}
              />
            </PanelSectionRow>
          </>
        )}
      </>
    ) : null,

    caps.sleepChargingIndicator ? (
      <>
        {sectionTitle(t("sleepCharging.section"))}
        <PanelSectionRow>
          <ToggleField
            label={t("sleepCharging.label")}
            description={t("sleepCharging.hint")}
            checked={sleepChargingIndicator}
            onChange={onSleepChargingIndicator}
            bottomSeparator="none"
          />
        </PanelSectionRow>
      </>
    ) : null,

    caps.experimental.length > 0 ? (
      <>
        {sectionTitle(t("experimental.title"))}
        {hint(t("experimental.description"))}
        {caps.experimental.map((feature) => (
          <PanelSectionRow key={feature}>
            <ToggleField
              label={t(`experimental.feature.${feature}`)}
              checked={caps.enabledExperiments.includes(feature)}
              onChange={(val) => onExperiment(feature, val)}
              bottomSeparator="none"
            />
          </PanelSectionRow>
        ))}
      </>
    ) : null,

    <>
      {sectionTitle(t("customize.title"))}
      <PanelSectionRow>
        <ButtonItem layout="below" bottomSeparator="none" onClick={() => openCustomizeModal(availableTabIds)}>
          {t("customize.button")}
        </ButtonItem>
      </PanelSectionRow>
      {hint(t("customize.button.desc"))}
    </>,

    <>
      {unvalidated ? hint(t("report.unvalidated.note")) : null}
      <PanelSectionRow>
        <ButtonItem
          layout="below"
          bottomSeparator="none"
          description={t("report.button.desc")}
          onClick={() => openReportModal(device)}
        >
          {t("report.button")}
        </ButtonItem>
      </PanelSectionRow>
    </>,

    <>
      {sectionTitle(t("about.title"))}
      <PanelSectionRow>
        <UpdatePanel lang={lang} version={version} />
      </PanelSectionRow>
      <PanelSectionRow>
        <div style={{ fontSize: 13, color: "rgba(255,255,255,0.7)", padding: "8px 2px 4px" }}>
          {madeByBefore}
          <Focusable
            onActivate={openChannel}
            onClick={openChannel}
            aria-label={AUTHOR}
            style={{ display: "inline", color: "#58a6ff", cursor: "pointer", textDecoration: "underline" }}
          >
            {AUTHOR}
          </Focusable>
          {madeByAfter}
        </div>
      </PanelSectionRow>
    </>,
  ].filter(Boolean);

  return (
    <>
      {sections.map((node, i) => (
        <Fragment key={i}>
          {i > 0 && <Divider />}
          {node}
        </Fragment>
      ))}
    </>
  );
};
