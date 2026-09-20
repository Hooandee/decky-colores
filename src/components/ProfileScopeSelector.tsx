import { Focusable } from "@decky/ui";
import { LuGamepad2 } from "react-icons/lu";

import { RunningApp } from "../apps/runningApp";
import { useI18n } from "../i18n";
import { ProfileScope } from "../types";
import { segmentGroupStyle, segmentItemStyle } from "./segmented";

interface Props {
  scope: ProfileScope;
  runningApp: RunningApp | null;
  disabled?: boolean;
  onSelect: (scope: ProfileScope) => void;
}

export function ProfileScopeSelector({
  scope,
  runningApp,
  disabled = false,
  onSelect,
}: Props) {
  const { t } = useI18n();
  const option = (selected: boolean): React.CSSProperties => ({
    ...segmentItemStyle(selected),
    flex: 1,
    minWidth: 0,
    padding: "6px 10px",
    textAlign: "center",
    cursor: disabled ? "default" : "pointer",
  });
  const select = (next: ProfileScope) => {
    if (!disabled) onSelect(next);
  };

  return (
    <div style={{ padding: "0 0 4px" }}>
      <Focusable
        role="radiogroup"
        aria-label={`${t("profiles.global")} / ${t("profiles.gameShort")}`}
        aria-busy={disabled || undefined}
        style={segmentGroupStyle}
      >
        <Focusable
          role="radio"
          aria-checked={scope === "global"}
          aria-disabled={disabled || undefined}
          onActivate={() => select("global")}
          onClick={() => select("global")}
          style={option(scope === "global")}
        >
          {t("profiles.global")}
        </Focusable>
        {runningApp && (
          <Focusable
            role="radio"
            aria-checked={scope === "game"}
            aria-disabled={disabled || undefined}
            onActivate={() => select("game")}
            onClick={() => select("game")}
            title={runningApp.name}
            style={option(scope === "game")}
          >
            <span
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: 4,
                minWidth: 0,
                width: "100%",
              }}
            >
              <LuGamepad2 size={13} style={{ flexShrink: 0 }} />
              <span>{t("profiles.gameShort")}</span>
            </span>
          </Focusable>
        )}
      </Focusable>
    </div>
  );
}
