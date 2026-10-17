import { FC, useState } from "react";
import { DialogButton, Focusable, ModalRoot, TextField, showModal } from "@decky/ui";
import { openFilePicker } from "@decky/api";
import type { FileSelectionType } from "@decky/api";

import { useI18n } from "../i18n";
import { SavedStyle, STYLE_NAME_MAX, STYLES_FOLDER_FALLBACK, StylesImport, styleSwatch, stylesForMode } from "../styles";
import { FocusRoot } from "./FocusRoot";

const BORDER = "rgba(255,255,255,0.08)";
const PICK_FILE = 0 as FileSelectionType;
const MAX_STYLES = 50;
const SUBTLE = { background: "rgba(255,255,255,0.06)", border: `1px solid ${BORDER}` };
const ACCENT = { background: "linear-gradient(135deg, rgb(var(--colores-accent-rgb)), #00bde8)", border: "none" };

export interface StyleActions {
  save: (name: string) => Promise<SavedStyle[]>;
  apply: (name: string) => Promise<void>;
  remove: (name: string) => Promise<SavedStyle[]>;
  exportAll: () => Promise<{ path: string; count: number }>;
  importFrom: (path: string) => Promise<StylesImport>;
}

interface Props {
  initial: SavedStyle[];
  actions: StyleActions;
  folder?: string;
  onApplied?: (style: SavedStyle) => void;
  closeModal?: () => void;
}

export const StylesModal: FC<Props> = ({ initial, actions, folder, onApplied, closeModal }) => {
  const { t } = useI18n();
  const [styles, setStyles] = useState(initial);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async (work: () => Promise<string | null>) => {
    setBusy(true);
    try {
      setNotice(await work());
    } catch (error) {
      console.error("Colores: styles action failed", error);
      setNotice(t("styles.error"));
    } finally {
      setBusy(false);
    }
  };

  const apply = async (style: SavedStyle) => {
    await actions.apply(style.name);
    onApplied?.(style);
    closeModal?.();
  };

  const importFile = () =>
    run(async () => {
      const picked = await openFilePicker(PICK_FILE, folder ?? STYLES_FOLDER_FALLBACK, true, true, undefined, ["json"]);
      const result = await actions.importFrom(picked.path);
      setStyles(result.styles);
      const only = result.imported.length === 1 ? result.styles.find((style) => style.name === result.imported[0]) : null;
      if (only) {
        await apply(only);
        return null;
      }
      return t("styles.imported", { added: result.added, replaced: result.replaced });
    });

  const exportFile = () =>
    run(async () => {
      const result = await actions.exportAll();
      return t("styles.exported", { path: result.path });
    });

  return (
    <ModalRoot closeModal={closeModal} onCancel={closeModal}>
      <FocusRoot>
        <Focusable style={{ display: "flex", flexDirection: "column", gap: 12, padding: 4 }}>
          <div>
            <div style={{ fontSize: 19, fontWeight: 800 }}>{t("styles.title")}</div>
            <div style={{ fontSize: 12, color: "rgba(255,255,255,0.55)", marginTop: 4, lineHeight: 1.45 }}>
              {t("styles.hint")}
            </div>
          </div>
          {styles.length === 0 ? (
            <div style={{ fontSize: 12, color: "rgba(255,255,255,0.45)", padding: "6px 2px" }}>{t("styles.empty")}</div>
          ) : (
            <Focusable style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {styles.map((style) => (
                <Focusable key={style.name} style={{ display: "flex", gap: 8 }}>
                  <DialogButton
                    disabled={busy}
                    onClick={() =>
                      run(async () => {
                        await apply(style);
                        return null;
                      })
                    }
                    style={{ ...SUBTLE, flex: 1, display: "flex", alignItems: "center", gap: 10, textAlign: "left" }}
                  >
                    <span style={{ width: 16, height: 16, borderRadius: 8, flexShrink: 0, background: styleSwatch(style) }} />
                    <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{style.name}</span>
                  </DialogButton>
                  <DialogButton
                    disabled={busy}
                    aria-label={t("styles.delete", { name: style.name })}
                    onClick={() =>
                      run(async () => {
                        setStyles(await actions.remove(style.name));
                        return null;
                      })
                    }
                    style={{ ...SUBTLE, width: 48, minWidth: 48, padding: 0 }}
                  >
                    ✕
                  </DialogButton>
                </Focusable>
              ))}
            </Focusable>
          )}
          <Focusable style={{ display: "flex", gap: 10 }}>
            <DialogButton disabled={busy || styles.length === 0} onClick={exportFile} style={{ ...SUBTLE, flex: 1 }}>
              {t("styles.export")}
            </DialogButton>
            <DialogButton disabled={busy} onClick={importFile} style={{ ...SUBTLE, flex: 1 }}>
              {t("styles.import")}
            </DialogButton>
          </Focusable>
          {notice && (
            <div style={{ fontSize: 11.5, color: "rgba(255,255,255,0.6)", lineHeight: 1.45, wordBreak: "break-all" }}>{notice}</div>
          )}
        </Focusable>
      </FocusRoot>
    </ModalRoot>
  );
};

export function openStylesModal(
  initial: SavedStyle[],
  actions: StyleActions,
  folder?: string,
  onApplied?: (style: SavedStyle) => void,
): void {
  showModal(<StylesModal initial={initial} actions={actions} folder={folder} onApplied={onApplied} />, window);
}

type SaveStep = "choose" | "pick" | "name";

const SaveStyleModal: FC<{
  styles: SavedStyle[];
  mode: string;
  actions: StyleActions;
  closeModal?: () => void;
}> = ({ styles, mode, actions, closeModal }) => {
  const { t } = useI18n();
  const sameMode = stylesForMode(styles, mode);
  const [step, setStep] = useState<SaveStep>(sameMode.length ? "choose" : "name");
  const [name, setName] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const store = async (wanted: string) => {
    const exists = styles.some((style) => style.name.toLowerCase() === wanted.toLowerCase());
    if (!exists && styles.length >= MAX_STYLES) {
      setNotice(t("styles.full"));
      return;
    }
    setBusy(true);
    try {
      await actions.save(wanted);
      closeModal?.();
    } catch (error) {
      console.error("Colores: style save failed", error);
      setNotice(t("styles.error"));
      setBusy(false);
    }
  };

  const overwrite = () => (sameMode.length === 1 ? store(sameMode[0].name) : setStep("pick"));

  return (
    <ModalRoot closeModal={closeModal} onCancel={closeModal}>
      <FocusRoot>
        <Focusable style={{ display: "flex", flexDirection: "column", gap: 12, padding: 4 }}>
          <div style={{ fontSize: 19, fontWeight: 800 }}>{t("styles.saveButton")}</div>
          {step === "choose" && (
            <>
              <div style={{ fontSize: 12, color: "rgba(255,255,255,0.55)", lineHeight: 1.45 }}>
                {t("styles.overwriteQuestion")}
              </div>
              <Focusable style={{ display: "flex", gap: 10 }}>
                <DialogButton disabled={busy} onClick={overwrite} style={{ ...SUBTLE, flex: 1 }}>
                  {sameMode.length === 1 ? t("styles.overwriteOne", { name: sameMode[0].name }) : t("styles.overwrite")}
                </DialogButton>
                <DialogButton disabled={busy} onClick={() => setStep("name")} style={{ ...ACCENT, flex: 1 }}>
                  {t("styles.saveNew")}
                </DialogButton>
              </Focusable>
            </>
          )}
          {step === "pick" && (
            <>
              <div style={{ fontSize: 12, color: "rgba(255,255,255,0.55)" }}>{t("styles.pickOverwrite")}</div>
              <Focusable style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {sameMode.map((style) => (
                  <DialogButton
                    key={style.name}
                    disabled={busy}
                    onClick={() => store(style.name)}
                    style={{ ...SUBTLE, display: "flex", alignItems: "center", gap: 10, textAlign: "left" }}
                  >
                    <span style={{ width: 16, height: 16, borderRadius: 8, flexShrink: 0, background: styleSwatch(style) }} />
                    <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{style.name}</span>
                  </DialogButton>
                ))}
              </Focusable>
            </>
          )}
          {step === "name" && (
            <>
              <TextField value={name} label={t("styles.name")} onChange={(event) => setName(event.target.value.slice(0, STYLE_NAME_MAX))} />
              <DialogButton disabled={busy || !name.trim()} onClick={() => store(name.trim())} style={ACCENT}>
                {t("styles.save")}
              </DialogButton>
            </>
          )}
          {notice && <div style={{ fontSize: 11.5, color: "rgba(255,255,255,0.6)" }}>{notice}</div>}
        </Focusable>
      </FocusRoot>
    </ModalRoot>
  );
};

export function openSaveStyleModal(styles: SavedStyle[], mode: string, actions: StyleActions): void {
  showModal(<SaveStyleModal styles={styles} mode={mode} actions={actions} />, window);
}
