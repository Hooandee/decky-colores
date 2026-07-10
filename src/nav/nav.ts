export function cycleTab(ids: string[], activeId: string, dir: -1 | 1): string {
  const i = ids.indexOf(activeId);
  if (i < 0 || ids.length === 0) return activeId;
  const n = ids.length;
  return ids[(i + dir + n) % n];
}
