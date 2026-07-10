import { FC } from "react";
import { PanelSectionRow } from "@decky/ui";

export const Divider: FC<{ margin?: string }> = ({ margin = "18px 0" }) => (
  <PanelSectionRow>
    <div style={{ height: 1, background: "rgba(255,255,255,0.07)", margin }} />
  </PanelSectionRow>
);
