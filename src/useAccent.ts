import { useSyncExternalStore } from "react";
import { Accent, applyAccentId, getAccentId, resolveAccent, subscribeAccent } from "./accent";

const KEY = "colores:accent";

function readStored(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

applyAccentId(readStored() ?? "");

export function setAccent(id: string): void {
  const resolved = resolveAccent(id).id;
  try {
    localStorage.setItem(KEY, resolved);
  } catch {
    void 0;
  }
  applyAccentId(resolved);
}

export function useAccent(): Accent {
  const id = useSyncExternalStore(subscribeAccent, getAccentId, getAccentId);
  return resolveAccent(id);
}
