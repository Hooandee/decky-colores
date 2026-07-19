import { CSSProperties, FC, ReactNode, useCallback } from "react";
import { ensureFocusStyles, COLORES_ROOT } from "../focus";
import { hexToRgbTriplet } from "../accent";
import { useAccent } from "../useAccent";

interface Props {
  children: ReactNode;
  style?: CSSProperties;
}

export const FocusRoot: FC<Props> = ({ children, style }) => {
  const accent = useAccent();
  const ref = useCallback((el: HTMLDivElement | null) => {
    if (el) ensureFocusStyles(el.ownerDocument);
  }, []);
  const vars = { ["--colores-accent-rgb"]: hexToRgbTriplet(accent.hex) } as CSSProperties;
  return (
    <div className={COLORES_ROOT} ref={ref} style={{ ...vars, ...style }}>
      {children}
    </div>
  );
};
