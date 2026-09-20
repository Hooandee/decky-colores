import { FC, ReactNode, useEffect, useRef } from "react";
import { Focusable } from "@decky/ui";

import { COLORES_TABSTRIP, focusAfterNavigation } from "../focus";
import { useI18n } from "../i18n";
import { theme } from "../theme";

export interface TabItem {
  id: string;
  icon: ReactNode;
  label: string;
  accent: string;
  badge?: ReactNode;
}

interface TabBarProps {
  tabs: TabItem[];
  activeId: string;
  disabled?: boolean;
  onSelect: (id: string) => void;
}

export function tabWindow<T extends { id: string }>(items: T[], activeId: string): {
  previous: T;
  active: T;
  next: T;
} | null {
  if (items.length === 0) return null;
  const found = items.findIndex((item) => item.id === activeId);
  const activeIndex = found < 0 ? 0 : found;
  return {
    previous: items[(activeIndex - 1 + items.length) % items.length],
    active: items[activeIndex],
    next: items[(activeIndex + 1) % items.length],
  };
}

export function focusActiveTab(
  root: Pick<HTMLElement, "querySelector">,
  activeId: string,
): (() => void) | undefined {
  const active = root.querySelector<HTMLElement>(`[data-section-id="${activeId}"]`);
  return active ? focusAfterNavigation(active) : undefined;
}

function ShoulderKey({ children }: { children: ReactNode }) {
  return (
    <span
      aria-hidden="true"
      style={{
        minWidth: 16,
        height: 14,
        padding: "0 2px",
        borderRadius: 4,
        boxSizing: "border-box",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        color: theme.color.textMuted,
        background: theme.color.surfaceRaised,
        boxShadow: `inset 0 0 0 1px ${theme.color.hairline}`,
        fontSize: 7.5,
        fontWeight: 800,
        letterSpacing: 0.2,
      }}
    >
      {children}
    </span>
  );
}

function SectionIcon({ item }: { item: TabItem }) {
  return (
    <span
      aria-hidden="true"
      style={{
        width: 28,
        height: 28,
        borderRadius: 8,
        background: item.accent,
        color: "white",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
        position: "relative",
      }}
    >
      {item.icon}
      {item.badge ? (
        <span style={{ position: "absolute", top: -2, right: -2 }}>{item.badge}</span>
      ) : null}
    </span>
  );
}

function SideSection({
  item,
  side,
  disabled,
  onSelect,
}: {
  item: TabItem;
  side: "left" | "right";
  disabled: boolean;
  onSelect: (id: string) => void;
}) {
  return (
    <Focusable
      data-section-id={item.id}
      role="button"
      aria-label={item.label}
      aria-disabled={disabled || undefined}
      onActivate={() => !disabled && onSelect(item.id)}
      onClick={() => !disabled && onSelect(item.id)}
      style={{
        minWidth: 0,
        width: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: side === "left" ? "flex-start" : "flex-end",
        gap: 2,
        cursor: disabled ? "default" : "pointer",
      }}
    >
      {side === "left" ? <ShoulderKey>L1</ShoulderKey> : null}
      <span
        style={{
          minWidth: 0,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
          color: theme.color.textMuted,
          fontSize: 9,
          fontWeight: 600,
          textAlign: side,
        }}
      >
        {item.label}
      </span>
      {item.badge}
      {side === "right" ? <ShoulderKey>R1</ShoulderKey> : null}
    </Focusable>
  );
}

export const TabBar: FC<TabBarProps> = ({ tabs, activeId, disabled = false, onSelect }) => {
  const { t } = useI18n();
  const rootRef = useRef<HTMLDivElement>(null);
  const mounted = useRef(false);
  const visible = tabWindow(tabs, activeId);

  useEffect(() => {
    if (!mounted.current) {
      mounted.current = true;
      return;
    }
    return rootRef.current ? focusActiveTab(rootRef.current, activeId) : undefined;
  }, [activeId]);

  if (!visible) return null;
  const showNeighbors = tabs.length > 1;

  return (
    <div
      ref={rootRef}
      className={COLORES_TABSTRIP}
      role="group"
      aria-label={t("nav.changeSection")}
      aria-busy={disabled || undefined}
      style={{
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) minmax(96px, 1.1fr) minmax(0, 1fr)",
        alignItems: "center",
        gap: theme.space.xs,
        minHeight: 42,
      }}
    >
      {showNeighbors ? (
        <SideSection item={visible.previous} side="left" disabled={disabled} onSelect={onSelect} />
      ) : <span />}
      <Focusable
        data-section-id={visible.active.id}
        aria-current="page"
        aria-label={visible.active.label}
        aria-disabled={disabled || undefined}
        onActivate={() => !disabled && onSelect(visible.active.id)}
        onClick={() => !disabled && onSelect(visible.active.id)}
        style={{
          minWidth: 0,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          gap: 6,
          color: theme.color.textPrimary,
        }}
      >
        <SectionIcon item={visible.active} />
        <span style={{ minWidth: 0, position: "relative", display: "flex" }}>
          <span
            style={{
              minWidth: 0,
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
              fontSize: theme.font.body,
              fontWeight: 750,
            }}
          >
            {visible.active.label}
          </span>
          <span
            aria-hidden="true"
            style={{
              position: "absolute",
              left: 0,
              right: 0,
              bottom: -4,
              height: 2,
              borderRadius: 1,
              background: visible.active.accent,
              pointerEvents: "none",
            }}
          />
        </span>
      </Focusable>
      {showNeighbors ? (
        <SideSection item={visible.next} side="right" disabled={disabled} onSelect={onSelect} />
      ) : <span />}
    </div>
  );
};
