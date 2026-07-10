import { useEffect, useRef } from "react";
import { cycleTab } from "./nav";

const LSHOULDER = 30;
const RSHOULDER = 31;

export function useShoulderNav(
  ids: string[],
  activeId: string,
  onSelect: (id: string) => void,
): void {
  const idsRef = useRef(ids);
  const activeRef = useRef(activeId);
  const selectRef = useRef(onSelect);
  useEffect(() => {
    idsRef.current = ids;
    activeRef.current = activeId;
    selectRef.current = onSelect;
  });

  useEffect(() => {
    let reg: { unregister?: () => void } | null = null;
    try {
      const input = SteamClient?.Input;
      if (!input || typeof input.RegisterForControllerInputMessages !== "function") return;
      reg = input.RegisterForControllerInputMessages((_idx: number, button: number, pressed: boolean) => {
        if (!pressed || (button !== LSHOULDER && button !== RSHOULDER)) return;
        const next = cycleTab(idsRef.current, activeRef.current, button === RSHOULDER ? 1 : -1);
        if (next !== activeRef.current) selectRef.current(next);
      });
    } catch {
      return;
    }
    return () => {
      try {
        reg?.unregister?.();
      } catch {
        return;
      }
    };
  }, []);
}
