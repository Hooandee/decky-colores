import { FC, ReactNode } from "react";
import { ModalRoot, showModal, Focusable, ButtonItem } from "@decky/ui";
import { LuChevronUp, LuChevronDown, LuEye, LuEyeOff, LuLock, LuCheck } from "react-icons/lu";

import { useI18n } from "../i18n";
import { tabMeta, PINNED_TAB } from "../nav/manifest";
import { orderIds, move, toggle } from "../nav/layout";
import { useLayout, saveLayout, resetLayout } from "../nav/store";
import { FocusRoot } from "./FocusRoot";
import { ACCENTS } from "../accent";
import { useAccent, setAccent } from "../useAccent";

const AccentPicker: FC = () => {
  const { t } = useI18n();
  const active = useAccent();
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      <div
        style={{
          fontSize: 11,
          color: "rgba(255,255,255,0.45)",
          textTransform: "uppercase",
          letterSpacing: 0.5,
        }}
      >
        {t("customize.accent")}
      </div>
      <div role="radiogroup" aria-label={t("customize.accent")} style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
        {ACCENTS.map((a) => {
          const on = a.id === active.id;
          return (
            <Focusable
              key={a.id}
              role="radio"
              aria-checked={on}
              aria-label={t(`accent.${a.id}`)}
              onActivate={() => setAccent(a.id)}
              onClick={() => setAccent(a.id)}
              style={{
                width: 34,
                height: 34,
                borderRadius: 999,
                background: a.hex,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                cursor: "pointer",
                boxShadow: on
                  ? `0 0 0 2px #0a0a0d, 0 0 0 4px ${a.hex}`
                  : "inset 0 0 0 1px rgba(255,255,255,0.07)",
              }}
            >
              {on && <LuCheck size={18} color="#fff" />}
            </Focusable>
          );
        })}
      </div>
    </div>
  );
};

const iconBtn = (dim: boolean): React.CSSProperties => ({
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  padding: 6,
  borderRadius: 8,
  color: dim ? "rgba(255,255,255,0.4)" : "rgba(255,255,255,0.9)",
  cursor: dim ? "default" : "pointer",
});

const Row: FC<{
  label: string;
  icon: ReactNode;
  index: number;
  count: number;
  hidden: boolean;
  locked: boolean;
  onMove: (dir: -1 | 1) => void;
  onToggle: () => void;
}> = ({ label, icon, index, count, hidden, locked, onMove, onToggle }) => {
  const { t } = useI18n();
  const first = index === 0;
  const last = index === count - 1;
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "6px 8px",
        borderRadius: 8,
        background: "rgba(255,255,255,0.04)",
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.07)",
        opacity: hidden ? 0.45 : 1,
      }}
    >
      <span style={{ display: "flex", color: "rgba(255,255,255,0.6)" }}>{icon}</span>
      <span
        style={{
          flex: 1,
          minWidth: 0,
          fontSize: 13,
          color: "rgba(255,255,255,0.9)",
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
        }}
      >
        {label}
      </span>
      <Focusable
        style={iconBtn(first)}
        aria-label={t("customize.moveUp")}
        onActivate={() => !first && onMove(-1)}
        onClick={() => !first && onMove(-1)}
      >
        <LuChevronUp size={18} style={{ opacity: first ? 0.3 : 1 }} />
      </Focusable>
      <Focusable
        style={iconBtn(last)}
        aria-label={t("customize.moveDown")}
        onActivate={() => !last && onMove(1)}
        onClick={() => !last && onMove(1)}
      >
        <LuChevronDown size={18} style={{ opacity: last ? 0.3 : 1 }} />
      </Focusable>
      {locked ? (
        <span style={iconBtn(true)} title={t("customize.locked")}>
          <LuLock size={16} />
        </span>
      ) : (
        <Focusable
          style={iconBtn(false)}
          aria-label={hidden ? t("customize.show") : t("customize.hide")}
          onActivate={onToggle}
          onClick={onToggle}
        >
          {hidden ? <LuEyeOff size={18} /> : <LuEye size={18} />}
        </Focusable>
      )}
    </div>
  );
};

const CustomizeBody: FC<{ availableIds: string[] }> = ({ availableIds }) => {
  const { t } = useI18n();
  const layout = useLayout();
  const order = orderIds(availableIds, layout.tabs.order);
  const hidden = layout.tabs.hidden;

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 16,
        padding: 8,
        maxWidth: 720,
        width: "100%",
        margin: "0 auto",
      }}
    >
      <div style={{ fontSize: 16, color: "rgba(255,255,255,0.95)" }}>{t("customize.title")}</div>

      <AccentPicker />

      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        {order.map((id, i) => {
          const meta = tabMeta(id);
          return (
            <Row
              key={id}
              label={meta ? t(meta.labelKey) : id}
              icon={meta?.icon}
              index={i}
              count={order.length}
              hidden={hidden.includes(id)}
              locked={id === PINNED_TAB}
              onMove={(dir) =>
                saveLayout({ ...layout, tabs: { order: move(order, id, dir), hidden } })
              }
              onToggle={() =>
                saveLayout({ ...layout, tabs: { order, hidden: toggle(hidden, id) } })
              }
            />
          );
        })}
      </div>

      <ButtonItem layout="below" onClick={() => resetLayout()}>
        {t("customize.reset")}
      </ButtonItem>
    </div>
  );
};

const CustomizeModal: FC<{ availableIds: string[]; closeModal?: () => void }> = ({
  availableIds,
  closeModal,
}) => (
  <ModalRoot closeModal={closeModal} bAllowFullSize>
    <FocusRoot>
      <CustomizeBody availableIds={availableIds} />
    </FocusRoot>
  </ModalRoot>
);

export function openCustomizeModal(availableIds: string[]): void {
  showModal(<CustomizeModal availableIds={availableIds} />, window);
}
