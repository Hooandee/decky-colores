import { useSyncExternalStore } from "react";
import { Layout, EMPTY_LAYOUT, coerceLayout } from "./layout";

const KEY = "colores:tabLayout";

let cache: Layout | null = null;
const listeners = new Set<() => void>();

function read(): Layout {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return EMPTY_LAYOUT;
    return coerceLayout(JSON.parse(raw));
  } catch {
    return EMPTY_LAYOUT;
  }
}

export function getLayout(): Layout {
  if (!cache) cache = read();
  return cache;
}

export function saveLayout(next: Layout): void {
  cache = next;
  try {
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    void 0;
  }
  listeners.forEach((l) => l());
}

export function resetLayout(): void {
  cache = EMPTY_LAYOUT;
  try {
    localStorage.removeItem(KEY);
  } catch {
    void 0;
  }
  listeners.forEach((l) => l());
}

function subscribe(cb: () => void): () => void {
  listeners.add(cb);
  return () => {
    listeners.delete(cb);
  };
}

export function useLayout(): Layout {
  return useSyncExternalStore(subscribe, getLayout, getLayout);
}
