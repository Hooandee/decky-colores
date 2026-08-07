import { Focusable } from "@decky/ui";

import { RunningApp } from "../apps/runningApp";
import { useI18n } from "../i18n";
import { ProfileScope, ProfileState } from "../types";

interface Props {
  scope: ProfileScope;
  runningApp: RunningApp | null;
  context: ProfileState;
  onSelect: (scope: ProfileScope) => void;
  onFollowGlobal: (follow: boolean) => void;
  onForget: () => void;
}

export function ProfileScopeSelector({
  scope,
  runningApp,
  context,
  onSelect,
  onFollowGlobal,
  onForget,
}: Props) {
  const { t } = useI18n();
  const option = (selected: boolean): React.CSSProperties => ({
    flex: 1,
    minWidth: 0,
    padding: "8px 10px",
    borderRadius: 9,
    background: selected ? "rgba(80, 160, 255, 0.28)" : "rgba(255,255,255,0.055)",
    boxShadow: selected
      ? "inset 0 0 0 1px rgba(130,190,255,0.75)"
      : "inset 0 0 0 1px rgba(255,255,255,0.08)",
    overflow: "hidden",
    whiteSpace: "nowrap",
    textOverflow: "ellipsis",
    fontSize: 13,
    fontWeight: selected ? 650 : 500,
  });

  return (
    <div style={{ display: "grid", gap: 7, padding: "0 0 4px" }}>
      <Focusable style={{ display: "flex", gap: 6 }}>
        <Focusable
          onActivate={() => onSelect("global")}
          onClick={() => onSelect("global")}
          style={option(scope === "global")}
        >
          {t("profiles.global")}
        </Focusable>
        {runningApp && (
          <Focusable
            onActivate={() => onSelect("game")}
            onClick={() => onSelect("game")}
            title={runningApp.name}
            style={option(scope === "game")}
          >
            {t("profiles.game", { name: runningApp.name })}
          </Focusable>
        )}
      </Focusable>
      {scope === "game" && runningApp && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 8,
            color: "rgba(255,255,255,0.58)",
            fontSize: 11,
            padding: "0 2px",
          }}
        >
          <span>
            {context.followsGlobal
              ? t("profiles.followingGlobal")
              : t("profiles.usingOwn")}
          </span>
          <Focusable style={{ display: "flex", gap: 5 }}>
            <Focusable
              onActivate={() => onFollowGlobal(!context.followsGlobal)}
              onClick={() => onFollowGlobal(!context.followsGlobal)}
              style={{ padding: "4px 7px", borderRadius: 6, background: "rgba(255,255,255,0.07)" }}
            >
              {context.followsGlobal ? t("profiles.useOwn") : t("profiles.followGlobal")}
            </Focusable>
            {context.hasGameProfile && (
              <Focusable
                onActivate={onForget}
                onClick={onForget}
                style={{ padding: "4px 7px", borderRadius: 6, background: "rgba(255,255,255,0.07)" }}
              >
                {t("profiles.forget")}
              </Focusable>
            )}
          </Focusable>
        </div>
      )}
    </div>
  );
}
