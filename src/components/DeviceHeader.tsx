import { FC } from "react";

import { rgbToCss } from "../color";
import { theme } from "../theme";
import { DeviceInfo, RGB } from "../types";

export const DeviceHeader: FC<{
  device: DeviceInfo;
  color: RGB;
}> = ({ device, color }) => {
  const detail = [device.product, device.board].find(
    (value) => value && value.toLowerCase() !== device.name.toLowerCase(),
  );
  const lightColor = rgbToCss(color);

  return (
    <div
      role="status"
      aria-label={device.name}
      style={{
        display: "inline-flex",
        alignItems: "center",
        alignSelf: "flex-start",
        gap: 6,
        width: "100%",
        maxWidth: "100%",
        minHeight: 24,
        padding: "3px 8px",
        borderRadius: 999,
        background: "rgba(255,255,255,0.04)",
        boxShadow: `inset 0 0 0 1px ${theme.color.hairline}`,
        boxSizing: "border-box",
      }}
    >
      <span
        aria-hidden="true"
        style={{
          width: 7,
          height: 7,
          flexShrink: 0,
          borderRadius: "50%",
          background: lightColor,
          boxShadow: `0 0 6px ${lightColor}`,
        }}
      />
      <span
        style={{
          minWidth: 0,
          color: theme.color.textPrimary,
          fontSize: theme.font.caption,
          fontWeight: 650,
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis",
        }}
      >
        {device.name}
      </span>
      {detail ? (
        <>
          <span aria-hidden="true" style={{ color: theme.color.textMuted, fontSize: 9 }}>
            •
          </span>
          <span
            style={{
              flexShrink: 0,
              color: theme.color.textMuted,
              fontSize: 9,
              fontWeight: 650,
              letterSpacing: "0.05em",
              textTransform: "uppercase",
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
            }}
          >
            {detail}
          </span>
        </>
      ) : null}
    </div>
  );
};
