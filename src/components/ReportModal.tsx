import { FC, useEffect, useState } from "react";
import { ModalRoot, showModal, Focusable, DialogButton, TextField } from "@decky/ui";

import { I18nProvider, useI18n } from "../i18n";
import { getState, submitReport, ReportResult } from "../api";
import {
  REPORT_CATEGORIES,
  ReportCategory,
  canSubmit,
  toggleCategory,
} from "../report/logic";

type Phase = "form" | "sending" | "done" | "error";

const ACCENT = "#58a6ff";
const MUTED = "rgba(255,255,255,0.55)";
const HAIRLINE = "rgba(255,255,255,0.14)";

const CategoryChip: FC<{ label: string; on: boolean; onClick: () => void }> = ({
  label,
  on,
  onClick,
}) => (
  <Focusable
    onActivate={onClick}
    onClick={onClick}
    style={{
      display: "flex",
      alignItems: "center",
      gap: 8,
      padding: "8px 12px",
      borderRadius: 8,
      boxShadow: `inset 0 0 0 1px ${on ? ACCENT : HAIRLINE}`,
      background: on ? "rgba(88,166,255,0.12)" : "transparent",
      fontSize: 14,
      color: "rgba(255,255,255,0.92)",
      flex: "1 1 45%",
      minWidth: 0,
      cursor: "pointer",
    }}
  >
    <div
      style={{
        width: 18,
        height: 18,
        flex: "0 0 auto",
        borderRadius: 5,
        boxShadow: `inset 0 0 0 2px ${on ? ACCENT : "rgba(255,255,255,0.4)"}`,
        background: on ? ACCENT : "transparent",
        color: "#08131f",
        fontSize: 12,
        lineHeight: "18px",
        textAlign: "center",
      }}
    >
      {on ? "✓" : ""}
    </div>
    <span>{label}</span>
  </Focusable>
);

const sectionLabel: React.CSSProperties = {
  fontSize: 11,
  fontWeight: 700,
  letterSpacing: "0.14em",
  textTransform: "uppercase",
  color: MUTED,
};

const card: React.CSSProperties = {
  borderRadius: 12,
  background: "rgba(255,255,255,0.04)",
  boxShadow: `inset 0 0 0 1px ${HAIRLINE}`,
};

const ReportBody: FC<{ closeModal?: () => void }> = ({ closeModal }) => {
  const { t } = useI18n();
  const [deviceName, setDeviceName] = useState<string>("");
  const [selected, setSelected] = useState<ReportCategory[]>([]);
  const [text, setText] = useState("");
  const [phase, setPhase] = useState<Phase>("form");
  const [result, setResult] = useState<ReportResult | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let alive = true;
    getState()
      .then((s) => alive && setDeviceName(s?.device?.name ?? ""))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  const submit = () => {
    setPhase("sending");
    submitReport(selected, text)
      .then((r) => {
        setResult(r);
        setPhase(r.ok ? "done" : "error");
      })
      .catch(() => {
        setResult({ ok: false, error: "network" });
        setPhase("error");
      });
  };

  const copy = () => {
    const code = result?.code;
    if (!code) return;
    const p = navigator.clipboard?.writeText(code);
    if (p) p.then(() => setCopied(true)).catch(() => setCopied(false));
  };

  const wrap = (children: React.ReactNode) => (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 18,
        padding: 8,
        maxWidth: 720,
        width: "100%",
        margin: "0 auto",
      }}
    >
      <div style={{ fontSize: 20, fontWeight: 700, color: "rgba(255,255,255,0.95)" }}>
        {t("report.title")}
      </div>
      {deviceName && (
        <div style={{ fontSize: 15, color: "rgba(255,255,255,0.85)" }}>{deviceName}</div>
      )}
      {children}
    </div>
  );

  if (phase === "sending") {
    return wrap(<div style={{ fontSize: 14, color: MUTED }}>{t("report.sending")}</div>);
  }

  if (phase === "done") {
    return wrap(
      <div style={{ display: "flex", flexDirection: "column", gap: 14, alignItems: "center", textAlign: "center" }}>
        <div style={{ fontSize: 40 }}>✅</div>
        <div style={{ fontSize: 16, color: "rgba(255,255,255,0.95)" }}>{t("report.done.title")}</div>
        <div style={{ fontSize: 14, color: MUTED }}>{t("report.done.thanks")}</div>
        <div style={{ ...card, padding: 14, minWidth: 220 }}>
          <div style={sectionLabel}>{t("report.code.label")}</div>
          <div style={{ fontSize: 26, fontWeight: 700, letterSpacing: 2, color: ACCENT, fontFamily: "monospace" }}>
            {result?.code}
          </div>
        </div>
        <div style={{ fontSize: 12, color: MUTED, maxWidth: 340, lineHeight: 1.45 }}>
          {t("report.code.hint")}
        </div>
        <Focusable style={{ display: "flex", gap: 8 }}>
          <DialogButton onClick={copy}>{copied ? t("report.copied") : t("report.copy")}</DialogButton>
          <DialogButton onClick={() => closeModal?.()}>{t("report.close")}</DialogButton>
        </Focusable>
      </div>,
    );
  }

  if (phase === "error") {
    return wrap(
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div style={{ fontSize: 14, color: "#ff7b72" }}>{t("report.error.title")}</div>
        {result?.saved_path && (
          <div style={{ fontSize: 12, color: MUTED }}>
            {t("report.error.saved", { path: result.saved_path })}
          </div>
        )}
        <Focusable style={{ display: "flex", gap: 8 }}>
          <DialogButton onClick={submit}>{t("report.retry")}</DialogButton>
          <DialogButton onClick={() => closeModal?.()}>{t("report.close")}</DialogButton>
        </Focusable>
      </div>,
    );
  }

  return wrap(
    <>
      <div style={{ fontSize: 14, color: MUTED, lineHeight: 1.45 }}>{t("report.intro")}</div>

      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        <div style={sectionLabel}>{t("report.section.what")}</div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
          {REPORT_CATEGORIES.map((id) => (
            <CategoryChip
              key={id}
              label={t(`report.cat.${id}`)}
              on={selected.includes(id)}
              onClick={() => setSelected((s) => toggleCategory(s, id))}
            />
          ))}
        </div>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        <div style={sectionLabel}>{t("report.section.describe")}</div>
        <TextField value={text} onChange={(e) => setText(e.target.value)} />
        {text.trim().length === 0 && (
          <div style={{ fontSize: 12, color: MUTED }}>{t("report.describe.hint")}</div>
        )}
      </div>

      <div
        style={{
          ...card,
          padding: 14,
          display: "flex",
          flexDirection: "column",
          gap: 4,
          fontSize: 12,
          color: MUTED,
          lineHeight: 1.5,
        }}
      >
        <div style={sectionLabel}>{t("report.privacy.title")}</div>
        <div><span style={{ color: "#3fb950" }}>●</span> {t("report.privacy.public")}</div>
        <div><span style={{ color: "#d29922" }}>●</span> {t("report.privacy.private")}</div>
        <div><span style={{ color: "#3fb950" }}>✓</span> {t("report.privacy.nopii")}</div>
      </div>

      <Focusable>
        <DialogButton disabled={!canSubmit(selected, text)} onClick={submit}>
          {t("report.send")}
        </DialogButton>
      </Focusable>
    </>,
  );
};

const ReportModal: FC<{ closeModal?: () => void }> = ({ closeModal }) => (
  <ModalRoot closeModal={closeModal} bAllowFullSize>
    <I18nProvider>
      <ReportBody closeModal={closeModal} />
    </I18nProvider>
  </ModalRoot>
);

export function openReportModal(): void {
  showModal(<ReportModal />, window);
}
