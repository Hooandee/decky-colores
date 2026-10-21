import { FC, useEffect, useMemo, useRef, useState } from "react";
import { DialogButton, Focusable, ModalRoot, SliderField, Spinner, showModal } from "@decky/ui";

import { previewColorCalibration, setColorCalibration } from "../api";
import { useI18n } from "../i18n";
import { createThrottle } from "../throttle";
import { CALIBRATION_STEPS, CalibrationSteps, NEUTRAL_CALIBRATION } from "../calibration";
import { FocusRoot } from "./FocusRoot";

const BORDER = "rgba(255,255,255,0.08)";
const PREVIEW_KEEPALIVE_MS = 30_000;

interface Props {
  initial: CalibrationSteps;
  closeModal?: () => void;
  onSaved: (steps: CalibrationSteps) => void;
}

export const ColorCalibrationModal: FC<Props> = ({ initial, closeModal, onSaved }) => {
  const { t } = useI18n();
  const [steps, setSteps] = useState<CalibrationSteps>(initial);
  const [index, setIndex] = useState(0);
  const [saving, setSaving] = useState(false);
  const saved = useRef(false);
  const step = CALIBRATION_STEPS[index];
  const last = index === CALIBRATION_STEPS.length - 1;
  const preview = useMemo(
    () =>
      createThrottle(
        (value: CalibrationSteps, sample: number[]) => previewColorCalibration(value, sample),
        80,
      ),
    [],
  );

  useEffect(() => {
    preview.run(steps, step.sample);
    const keepAlive = setInterval(() => preview.run(steps, step.sample), PREVIEW_KEEPALIVE_MS);
    return () => clearInterval(keepAlive);
  }, [preview, steps, step]);

  useEffect(
    () => () => {
      preview.cancel();
      if (!saved.current) void previewColorCalibration(null, null).catch(() => undefined);
    },
    [preview],
  );

  const save = async () => {
    setSaving(true);
    preview.cancel();
    try {
      onSaved(await setColorCalibration(steps));
      saved.current = true;
      closeModal?.();
    } catch (error) {
      console.error("Colores: color calibration save failed", error);
      setSaving(false);
    }
  };

  const [r, g, b] = step.sample;
  return (
    <ModalRoot closeModal={closeModal} onCancel={saving ? undefined : closeModal} bDisableBackgroundDismiss={saving}>
      <FocusRoot>
        <Focusable style={{ display: "flex", flexDirection: "column", gap: 12, padding: 4 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div
              style={{
                width: 34,
                height: 34,
                borderRadius: 17,
                flexShrink: 0,
                background: `rgb(${r}, ${g}, ${b})`,
                boxShadow: `0 0 18px rgba(${r}, ${g}, ${b}, 0.45)`,
              }}
            />
            <div>
              <div style={{ fontSize: 10, color: "rgba(255,255,255,0.45)", textTransform: "uppercase", letterSpacing: "0.12em" }}>
                {t("calibration.title")} · {index + 1}/{CALIBRATION_STEPS.length}
              </div>
              <div style={{ fontSize: 19, fontWeight: 800, marginTop: 2 }}>{t(`calibration.${step.id}`)}</div>
            </div>
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.55)", lineHeight: 1.45 }}>
            {t(`calibration.${step.id}.hint`)}
          </div>
          <SliderField
            value={steps[step.id]}
            min={-step.limit}
            max={step.limit}
            step={1}
            notchCount={3}
            notchLabels={[
              { notchIndex: 0, label: t(`calibration.${step.id}.less`) },
              { notchIndex: 2, label: t(`calibration.${step.id}.more`) },
            ]}
            disabled={saving}
            onChange={(value) => setSteps({ ...steps, [step.id]: value })}
          />
          <Focusable style={{ display: "flex", gap: 10 }}>
            <DialogButton
              disabled={saving}
              onClick={() => (index === 0 ? setSteps(NEUTRAL_CALIBRATION) : setIndex(index - 1))}
              style={{ flex: 1, background: "rgba(255,255,255,0.06)", border: `1px solid ${BORDER}` }}
            >
              {t(index === 0 ? "calibration.reset" : "calibration.back")}
            </DialogButton>
            <DialogButton
              disabled={saving}
              onClick={last ? save : () => setIndex(index + 1)}
              style={{ flex: 1, background: "linear-gradient(135deg, rgb(var(--colores-accent-rgb)), #00bde8)", border: "none" }}
            >
              {saving ? <Spinner width={18} height={18} /> : t(last ? "calibration.save" : "calibration.next")}
            </DialogButton>
          </Focusable>
        </Focusable>
      </FocusRoot>
    </ModalRoot>
  );
};

export function openColorCalibrationModal(
  initial: CalibrationSteps,
  onSaved: (steps: CalibrationSteps) => void,
): void {
  showModal(<ColorCalibrationModal initial={initial} onSaved={onSaved} />, window);
}
