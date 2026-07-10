export interface ListPref {
  order: string[];
  hidden: string[];
}

export interface Layout {
  version: 1;
  tabs: ListPref;
}

export const EMPTY_LAYOUT: Layout = { version: 1, tabs: { order: [], hidden: [] } };

export function orderIds(defaults: string[], order: string[] | undefined): string[] {
  const known = new Set(defaults);
  const seen = new Set<string>();
  const result: string[] = [];
  for (const id of order ?? []) {
    if (known.has(id) && !seen.has(id)) {
      result.push(id);
      seen.add(id);
    }
  }
  for (const id of defaults) {
    if (!seen.has(id)) result.push(id);
  }
  return result;
}

export function visibleIds(
  defaults: string[],
  pref: ListPref | undefined,
  pinned: string[] = [],
): string[] {
  const hidden = new Set(pref?.hidden ?? []);
  const pin = new Set(pinned);
  return orderIds(defaults, pref?.order).filter((id) => pin.has(id) || !hidden.has(id));
}

export function move(list: string[], id: string, dir: -1 | 1): string[] {
  const i = list.indexOf(id);
  if (i < 0) return list;
  const j = i + dir;
  if (j < 0 || j >= list.length) return list;
  const copy = list.slice();
  [copy[i], copy[j]] = [copy[j], copy[i]];
  return copy;
}

export function toggle(set: string[], id: string): string[] {
  return set.includes(id) ? set.filter((x) => x !== id) : [...set, id];
}

const strArray = (v: unknown): string[] =>
  Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];

const asPref = (v: unknown): ListPref => {
  const o = (v && typeof v === "object" ? v : {}) as { order?: unknown; hidden?: unknown };
  return { order: strArray(o.order), hidden: strArray(o.hidden) };
};

// Tolerant of corrupt/old localStorage shapes: anything unrecognized degrades to
// the empty layout so a bad value can never throw during render and brick the panel.
export function coerceLayout(parsed: unknown): Layout {
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    return { version: 1, tabs: { order: [], hidden: [] } };
  }
  const p = parsed as { tabs?: unknown };
  return { version: 1, tabs: asPref(p.tabs) };
}
