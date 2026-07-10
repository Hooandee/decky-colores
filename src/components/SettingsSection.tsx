import { FC, Fragment, ReactNode, useEffect, useState } from "react";
import { ButtonItem, Focusable, Navigation, PanelSectionRow, ToggleField } from "@decky/ui";

import { getVersion } from "../api";
import { useI18n, LangToggle, type Lang } from "../i18n";
import { UpdatePanel } from "../updater/UpdatePanel";
import { openCustomizeModal } from "./CustomizeModal";
import { openReportModal } from "./ReportModal";
import { Capabilities } from "../types";

const AUTHOR = "Hooandee";
const YOUTUBE_URL = "https://www.youtube.com/@Hooandee";

let versionCache = "";

const divider = (
  <PanelSectionRow>
    <div style={{ height: 1, background: "rgba(255,255,255,0.07)", margin: "18px 0" }} />
  </PanelSectionRow>
);

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
  availableTabIds: string[];
  lang: Lang;
  forceControl: boolean;
  powerLedOff: boolean;
  onForceControl: (v: boolean) => void;
  onPowerLed: (v: boolean) => void;
  onExperiment: (feature: string, val: boolean) => void;
  onReconnect: () => void;
}

export const SettingsSection: FC<SettingsSectionProps> = ({
  caps,
  availableTabIds,
  lang,
  forceControl,
  powerLedOff,
  onForceControl,
  onPowerLed,
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
        <LangToggle />
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
        <PanelSectionRow>
          <ToggleField label={t("powerLed.label")} checked={powerLedOff} onChange={onPowerLed} bottomSeparator="none" />
        </PanelSectionRow>
        {hint(t("powerLed.warning"))}
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
        <ButtonItem layout="below" bottomSeparator="none" description={t("report.button.desc")} onClick={() => openReportModal()}>
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
          {i > 0 && divider}
          {node}
        </Fragment>
      ))}
    </>
  );
};
