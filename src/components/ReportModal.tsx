import {
  CSSProperties,
  FC,
  ReactNode,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import {
  DialogButton,
  Focusable,
  getFocusNavController,
  ModalRoot,
  showModal,
  TextField,
} from "@decky/ui";
import { LuBug, LuLightbulb } from "react-icons/lu";

import { ReportResult, submitReport } from "../api";
import { I18nProvider, useI18n } from "../i18n";
import {
  canSubmit,
  REPORT_CATEGORIES,
  ReportCategory,
  ReportKind,
  reportPresentation,
  toggleCategory,
} from "../report/logic";
import { theme } from "../theme";
import { DeviceInfo } from "../types";
import { FocusRoot } from "./FocusRoot";

type Phase = "form" | "sending" | "done" | "error";

const ReportKindCard: FC<{
  label: string;
  icon: ReactNode;
  selected: boolean;
  color: string;
  tint: string;
  preferredFocus?: boolean;
  onSelect: () => void;
}> = ({ label, icon, selected, color, tint, preferredFocus, onSelect }) => {
  const [focused, setFocused] = useState(false);
  return (
    <Focusable
      role="radio"
      aria-label={label}
      aria-checked={selected}
      {...(preferredFocus ? { preferredFocus: true } : {})}
      onActivate={onSelect}
      onClick={onSelect}
      onGamepadFocus={() => setFocused(true)}
      onGamepadBlur={() => setFocused(false)}
      noFocusRing
      style={{
        ...theme.card,
        flex: "1 1 0",
        minWidth: 0,
        minHeight: 150,
        padding: theme.space.lg,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: theme.space.md,
        textAlign: "center",
        color: theme.color.textPrimary,
        background: focused || selected ? tint : theme.color.surfaceRaised,
        boxShadow: `inset 0 0 0 ${focused ? 2 : 1}px ${focused || selected ? color : theme.color.hairline}`,
      }}
    >
      <div
        aria-hidden="true"
        style={{
          width: 56,
          height: 56,
          borderRadius: theme.radius.md,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          color,
          background: tint,
          boxShadow: `inset 0 0 0 1px ${color}55`,
        }}
      >
        {icon}
      </div>
      <span style={{ fontSize: 20, fontWeight: 700, lineHeight: 1.15 }}>{label}</span>
    </Focusable>
  );
};

const SelectionChip: FC<{
  label: string;
  on: boolean;
  onClick: () => void;
}> = ({ label, on, onClick }) => {
  const [focused, setFocused] = useState(false);
  return (
    <Focusable
      {...selectionChipA11y(on)}
      data-report-category="true"
      aria-label={label}
      onActivate={onClick}
      onClick={onClick}
      onGamepadFocus={() => setFocused(true)}
      onGamepadBlur={() => setFocused(false)}
      noFocusRing
      style={selectionChipStyle(on, focused)}
    >
    <div
      aria-hidden="true"
      style={{
        width: 18,
        height: 18,
        flex: "0 0 auto",
        borderRadius: 5,
        boxShadow: `inset 0 0 0 2px ${on ? theme.color.accent : theme.color.textMuted}`,
        background: on ? theme.color.accent : "transparent",
        color: theme.color.onAccent,
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
};

export function selectionChipStyle(on: boolean, focused: boolean): CSSProperties {
  return {
    display: "flex",
    alignItems: "center",
    gap: theme.space.sm,
    padding: `${theme.space.sm}px ${theme.space.md}px`,
    borderRadius: theme.radius.sm,
    boxShadow: `inset 0 0 0 ${focused ? 2 : 1}px ${focused || on ? theme.color.accent : theme.color.hairline}`,
    background: on ? `rgba(${theme.color.accentRgb},0.12)` : "transparent",
    fontSize: theme.font.body,
    color: theme.color.textPrimary,
    flex: "1 1 45%",
    minWidth: 0,
  };
}

export function selectionChipA11y(on: boolean) {
  return { role: "checkbox" as const, "aria-checked": on };
}

export function reportFocusTarget(
  root: Pick<HTMLElement, "querySelector">,
  phase: Phase,
  choosingKind: boolean,
  kind: ReportKind | null,
): HTMLElement | null {
  if (phase === "sending") return null;
  if (phase === "done" || phase === "error") {
    return root.querySelector<HTMLElement>('[data-report-primary-action="true"]');
  }
  if (kind === null) return null;
  const selector = choosingKind
    ? '[role="radio"][aria-checked="true"]'
    : '[data-report-category="true"]';
  return root.querySelector<HTMLElement>(selector);
}

const ReportBody: FC<{ device: DeviceInfo; closeModal?: () => void }> = ({
  device,
  closeModal,
}) => {
  const { t } = useI18n();
  const [kind, setKind] = useState<ReportKind | null>(null);
  const [choosingKind, setChoosingKind] = useState(true);
  const [selected, setSelected] = useState<ReportCategory[]>([]);
  const [text, setText] = useState("");
  const [phase, setPhase] = useState<Phase>("form");
  const [result, setResult] = useState<ReportResult | null>(null);
  const [copied, setCopied] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  const submit = () => {
    if (kind === null) return;
    setPhase("sending");
    submitReport(selected, text, kind)
      .then((response) => {
        setResult(response);
        setPhase(response.ok ? "done" : "error");
      })
      .catch(() => {
        setResult({ ok: false, error: "network" });
        setPhase("error");
      });
  };

  const copy = () => {
    const code = result?.code;
    if (!code) return;
    const promise = navigator.clipboard?.writeText(code);
    if (promise) promise.then(() => setCopied(true)).catch(() => setCopied(false));
  };

  const wrap = (children: ReactNode) => (
    <div
      ref={rootRef}
      style={{
        display: "flex",
        flexDirection: "column",
        gap: theme.space.lg,
        padding: theme.space.sm,
        maxWidth: 720,
        width: "100%",
        margin: "0 auto",
      }}
    >
      <div style={{ fontSize: theme.font.value, color: theme.color.textPrimary }}>
        {device.name}
      </div>
      {children}
    </div>
  );
  const presentation = reportPresentation(kind ?? "bug");
  const chooseKind = (next: ReportKind) => {
    setKind(next);
    setChoosingKind(false);
  };

  useLayoutEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    const target = reportFocusTarget(root, phase, choosingKind, kind);
    if (!target) return;
    try {
      const controller = getFocusNavController();
      if (typeof controller?.FocusElement === "function") {
        controller.FocusElement(target);
        return;
      }
    } catch {}
    target.focus({ preventScroll: true });
  }, [phase, choosingKind, kind]);

  if (phase === "sending") {
    return wrap(
      <div style={{ fontSize: theme.font.body, color: theme.color.textMuted }}>
        {t("report.sending")}
      </div>,
    );
  }

  if (phase === "done") {
    return wrap(
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: theme.space.md,
          alignItems: "center",
          textAlign: "center",
        }}
      >
        <div style={{ fontSize: 40 }}>✅</div>
        <div style={{ fontSize: theme.font.value, color: theme.color.textPrimary }}>
          {t("report.done.title")}
        </div>
        <div style={{ fontSize: theme.font.body, color: theme.color.textMuted }}>
          {t(presentation.doneThanks)}
        </div>
        <div style={{ ...theme.card, padding: theme.space.md, minWidth: 220 }}>
          <div style={theme.sectionLabel}>{t("report.code.label")}</div>
          <div
            style={{
              fontSize: 26,
              fontWeight: 700,
              letterSpacing: 2,
              color: theme.color.accent,
              fontFamily: "monospace",
            }}
          >
            {result?.code}
          </div>
        </div>
        <div style={{ fontSize: theme.font.caption, color: theme.color.textMuted, maxWidth: 340 }}>
          {t(presentation.codeHint)}
        </div>
        <Focusable style={{ display: "flex", gap: theme.space.sm }}>
          <DialogButton data-report-primary-action="true" onClick={copy}>
            {copied ? t("report.copied") : t("report.copy")}
          </DialogButton>
          <DialogButton onClick={() => closeModal?.()}>{t("report.close")}</DialogButton>
        </Focusable>
      </div>,
    );
  }

  if (phase === "error") {
    return wrap(
      <div style={{ display: "flex", flexDirection: "column", gap: theme.space.md }}>
        <div style={{ fontSize: theme.font.body, color: theme.color.danger }}>
          {t("report.error.title")}
        </div>
        {result?.saved_path ? (
          <div style={{ fontSize: theme.font.caption, color: theme.color.textMuted }}>
            {t("report.error.saved", { path: result.saved_path })}
          </div>
        ) : null}
        <Focusable style={{ display: "flex", gap: theme.space.sm }}>
          <DialogButton data-report-primary-action="true" onClick={submit}>
            {t("report.retry")}
          </DialogButton>
          <DialogButton onClick={() => closeModal?.()}>{t("report.close")}</DialogButton>
        </Focusable>
      </div>,
    );
  }

  if (choosingKind || kind === null) {
    return wrap(
      <>
        <div style={{ textAlign: "center" }}>
          <div
            style={{
              fontSize: theme.font.value,
              fontWeight: 700,
              color: theme.color.textPrimary,
            }}
          >
            {t("report.title")}
          </div>
          <div
            style={{
              marginTop: theme.space.xs,
              fontSize: theme.font.body,
              color: theme.color.textMuted,
            }}
          >
            {t("report.section.kind")}
          </div>
        </div>
        <Focusable
          role="radiogroup"
          aria-label={t("report.section.kind")}
          flow-children="row"
          noFocusRing
          style={{ display: "flex", gap: theme.space.md, width: "100%" }}
        >
          <ReportKindCard
            label={t("report.kind.bug")}
            icon={<LuBug size={36} />}
            selected={kind === "bug"}
            color={theme.color.danger}
            tint="rgba(224,90,90,0.12)"
            preferredFocus={kind === null}
            onSelect={() => chooseKind("bug")}
          />
          <ReportKindCard
            label={t("report.kind.feature")}
            icon={<LuLightbulb size={36} />}
            selected={kind === "feature"}
            color={theme.color.accent}
            tint={`rgba(${theme.color.accentRgb},0.12)`}
            onSelect={() => chooseKind("feature")}
          />
        </Focusable>
      </>,
    );
  }

  return wrap(
    <>
      <div
        style={{
          ...theme.card,
          padding: `${theme.space.sm}px ${theme.space.md}px`,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: theme.space.md,
        }}
      >
        <div
          style={{
            display: "flex",
            flex: "1 1 auto",
            minWidth: 0,
            alignItems: "center",
            gap: theme.space.sm,
            color: theme.color.textPrimary,
          }}
        >
          {kind === "bug"
            ? <LuBug size={22} color={theme.color.danger} aria-hidden="true" />
            : <LuLightbulb size={22} color={theme.color.accent} aria-hidden="true" />}
          <span style={{ fontSize: theme.font.body, fontWeight: 700 }}>
            {t(`report.kind.${kind}`)}
          </span>
        </div>
        <DialogButton
          style={{ width: 112, minWidth: 112, flex: "0 0 auto" }}
          onClick={() => setChoosingKind(true)}
        >
          {t("report.kind.change")}
        </DialogButton>
      </div>

      <div style={{ fontSize: theme.font.body, color: theme.color.textMuted }}>
        {t(presentation.intro)}
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: theme.space.sm }}>
        <div style={theme.sectionLabel}>{t(presentation.sectionWhat)}</div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: theme.space.sm }}>
          {REPORT_CATEGORIES.map((id) => (
            <SelectionChip
              key={id}
              label={t(`report.cat.${id}`)}
              on={selected.includes(id)}
              onClick={() => setSelected((current) => toggleCategory(current, id))}
            />
          ))}
        </div>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: theme.space.sm }}>
        <div style={theme.sectionLabel}>{t(presentation.sectionDescribe)}</div>
        <TextField value={text} onChange={(event) => setText(event.target.value)} />
        {text.trim().length === 0 ? (
          <div style={{ fontSize: theme.font.caption, color: theme.color.textMuted }}>
            {t(presentation.describeHint)}
          </div>
        ) : null}
      </div>

      <div
        style={{
          ...theme.card,
          padding: theme.space.md,
          display: "flex",
          flexDirection: "column",
          gap: theme.space.xs,
          fontSize: theme.font.caption,
          color: theme.color.textMuted,
          lineHeight: 1.5,
        }}
      >
        <div style={theme.sectionLabel}>{t("report.privacy.title")}</div>
        <div>
          <span style={{ color: theme.color.ok }}>●</span> {t("report.privacy.public")}
        </div>
        <div>
          <span style={{ color: theme.color.warn }}>●</span> {t("report.privacy.private")}
        </div>
        <div>
          <span style={{ color: theme.color.ok }}>✓</span> {t("report.privacy.nopii")}
        </div>
      </div>

      <Focusable>
        <DialogButton disabled={!canSubmit(selected, text)} onClick={submit}>
          {t("report.send")}
        </DialogButton>
      </Focusable>
    </>,
  );
};

const ReportModal: FC<{ device: DeviceInfo; closeModal?: () => void }> = ({
  device,
  closeModal,
}) => (
  <ModalRoot closeModal={closeModal} bAllowFullSize>
    <FocusRoot>
      <I18nProvider>
        <ReportBody device={device} closeModal={closeModal} />
      </I18nProvider>
    </FocusRoot>
  </ModalRoot>
);

export function openReportModal(device: DeviceInfo): void {
  showModal(<ReportModal device={device} />, window);
}
