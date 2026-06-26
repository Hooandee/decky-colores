import { PanelSection, PanelSectionRow, staticClasses } from "@decky/ui";
import { callable, definePlugin } from "@decky/api";
import { useEffect, useState } from "react";
import { FaPalette } from "react-icons/fa";

const getVersion = callable<[], string>("get_version");

function Content() {
  const [version, setVersion] = useState<string>("…");

  useEffect(() => {
    getVersion().then(setVersion);
  }, []);

  return (
    <PanelSection title="Colores">
      <PanelSectionRow>
        <div>Backend version: {version}</div>
      </PanelSectionRow>
    </PanelSection>
  );
}

export default definePlugin(() => ({
  name: "Colores",
  titleView: <div className={staticClasses.Title}>Colores</div>,
  content: <Content />,
  icon: <FaPalette />,
  onDismount() {},
}));
