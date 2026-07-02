import { FC, useEffect, useState } from "react";
import { Focusable, Navigation } from "@decky/ui";

import { getVersion } from "../api";
import { useI18n, type Lang } from "../i18n";
import { AlertDot } from "../updater/AlertDot";
import { UpdatePanel } from "../updater/UpdatePanel";

const AUTHOR = "Hooandee";
const YOUTUBE_URL = "https://www.youtube.com/@Hooandee";

export const About: FC<{ lang: Lang; hasUpdate: boolean }> = ({ lang, hasUpdate }) => {
  const { t } = useI18n();
  const [version, setVersion] = useState<string>("");

  useEffect(() => {
    let alive = true;
    getVersion()
      .then((v) => alive && setVersion(v))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  const openChannel = () => Navigation.NavigateToExternalWeb(YOUTUBE_URL);
  const [before, after] = t("about.madeBy").split("{name}");

  return (
    <div style={{ padding: "4px 2px 2px" }}>
      <div style={{ height: 1, background: "rgba(255,255,255,0.07)", margin: "14px 0 10px" }} />
      <div
        style={{
          display: "flex",
          alignItems: "center",
          fontSize: 11,
          fontWeight: 700,
          letterSpacing: "0.14em",
          textTransform: "uppercase",
          color: "rgba(255,255,255,0.5)",
          marginBottom: 8,
        }}
      >
        {t("about.title")}
        <AlertDot show={hasUpdate} />
      </div>

      <div style={{ marginBottom: 8 }}>
        <UpdatePanel lang={lang} version={version} />
      </div>
      <div style={{ fontSize: 13, color: "rgba(255,255,255,0.7)", marginBottom: 4 }}>
        {before}
        <Focusable
          onActivate={openChannel}
          onClick={openChannel}
          aria-label={AUTHOR}
          style={{
            display: "inline",
            color: "#58a6ff",
            cursor: "pointer",
            textDecoration: "underline",
          }}
        >
          {AUTHOR}
        </Focusable>
        {after}
      </div>
      <div style={{ fontSize: 11, color: "rgba(255,255,255,0.45)" }}>{t("about.basedOn")}</div>
    </div>
  );
};
