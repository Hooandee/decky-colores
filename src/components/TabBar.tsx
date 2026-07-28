import { FC, ReactNode, useEffect, useRef } from "react";
import { Focusable } from "@decky/ui";
import { segmentGroupStyle, segmentItemStyle } from "./segmented";
import { MarqueeText } from "./MarqueeText";
import { COLORES_TABSTRIP, focusAfterNavigation } from "../focus";

export interface TabItem {
  id: string;
  icon: ReactNode;
  label: string;
  badge?: ReactNode;
}

interface TabBarProps {
  tabs: TabItem[];
  activeId: string;
  onSelect: (id: string) => void;
}

const ACTIVE_LABEL_MAX = 150;
const FADE_W = 22;
const TAB_RADIUS = 12;
const TAB_SURFACE = "rgba(255,255,255,0.04)";
const FADE_BASE = {
  position: "absolute" as const,
  top: 0,
  bottom: 0,
  width: FADE_W,
  pointerEvents: "none" as const,
  opacity: 0,
  transition: "opacity 140ms ease",
  zIndex: 2,
};

export const TabBar: FC<TabBarProps> = ({ tabs, activeId, onSelect }) => {
  const stripRef = useRef<HTMLDivElement>(null);
  const activeTabRef = useRef<HTMLDivElement>(null);
  const leftFadeRef = useRef<HTMLDivElement>(null);
  const rightFadeRef = useRef<HTMLDivElement>(null);
  const mounted = useRef(false);

  const reconcile = () => {
    const strip = stripRef.current;
    if (!strip) return;
    const overflow = strip.scrollWidth - strip.clientWidth;
    strip.style.justifyContent = overflow > 1 ? "flex-start" : "center";
    const left = strip.scrollLeft;
    if (leftFadeRef.current) leftFadeRef.current.style.opacity = left > 1 ? "1" : "0";
    if (rightFadeRef.current)
      rightFadeRef.current.style.opacity = left < overflow - 1 ? "1" : "0";
  };

  useEffect(() => {
    const activeTab = activeTabRef.current;
    activeTab?.scrollIntoView({
      inline: "center",
      block: "nearest",
      behavior: mounted.current ? "smooth" : "auto",
    });
    let cancelFocus: (() => void) | undefined;
    if (mounted.current && activeTab) cancelFocus = focusAfterNavigation(activeTab);
    else mounted.current = true;
    return cancelFocus;
  }, [activeId]);

  useEffect(reconcile);

  useEffect(() => {
    const strip = stripRef.current;
    if (!strip) return;
    strip.addEventListener("scroll", reconcile, { passive: true });
    const ro =
      typeof ResizeObserver !== "undefined" ? new ResizeObserver(reconcile) : null;
    ro?.observe(strip);
    return () => {
      strip.removeEventListener("scroll", reconcile);
      ro?.disconnect();
    };
  }, [tabs.length]);

  return (
    <div style={{ position: "relative" }}>
      <Focusable
        ref={stripRef}
        className={COLORES_TABSTRIP}
        style={{
          ...segmentGroupStyle,
          padding: "8px 10px",
          overflowX: "auto",
          overflowY: "hidden",
        }}
      >
        {tabs.map((tab) => {
          const active = tab.id === activeId;
          return (
            <Focusable
              key={tab.id}
              ref={active ? activeTabRef : undefined}
              style={{
                ...segmentItemStyle(active),
                flex: "0 0 auto",
                maxWidth: active ? ACTIVE_LABEL_MAX : undefined,
                padding: active ? "6px 10px" : "6px 9px",
              }}
              aria-label={tab.label}
              onActivate={() => onSelect(tab.id)}
              onClick={() => onSelect(tab.id)}
            >
              {tab.icon}
              {active && <MarqueeText text={tab.label} />}
              {tab.badge}
            </Focusable>
          );
        })}
      </Focusable>
      <div
        ref={leftFadeRef}
        style={{
          ...FADE_BASE,
          left: 0,
          borderTopLeftRadius: TAB_RADIUS,
          borderBottomLeftRadius: TAB_RADIUS,
          background: `linear-gradient(to right, ${TAB_SURFACE}, transparent)`,
        }}
      />
      <div
        ref={rightFadeRef}
        style={{
          ...FADE_BASE,
          right: 0,
          borderTopRightRadius: TAB_RADIUS,
          borderBottomRightRadius: TAB_RADIUS,
          background: `linear-gradient(to left, ${TAB_SURFACE}, transparent)`,
        }}
      />
    </div>
  );
};
