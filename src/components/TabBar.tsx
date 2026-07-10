import { FC, ReactNode } from "react";
import { Focusable } from "@decky/ui";
import { segmentGroupStyle, segmentItemStyle } from "./segmented";
import { MarqueeText } from "./MarqueeText";

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

export const TabBar: FC<TabBarProps> = ({ tabs, activeId, onSelect }) => {
  return (
    <Focusable style={segmentGroupStyle}>
      {tabs.map((tab) => {
        const active = tab.id === activeId;
        return (
          <Focusable
            key={tab.id}
            style={{
              ...segmentItemStyle(active),
              flex: active ? 1 : "0 0 auto",
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
  );
};
