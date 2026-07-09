export const REPORT_CATEGORIES = [
  "color",
  "brightness",
  "effects",
  "ambilight",
  "battery",
  "powerLed",
  "other",
] as const;

export type ReportCategory = (typeof REPORT_CATEGORIES)[number];

export function toggleCategory(
  selected: ReportCategory[],
  id: ReportCategory,
): ReportCategory[] {
  return selected.includes(id)
    ? selected.filter((x) => x !== id)
    : [...selected, id];
}

export function canSubmit(_selected: ReportCategory[], text: string): boolean {
  return text.trim().length > 0;
}
