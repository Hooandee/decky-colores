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
export type ReportKind = "bug" | "feature";

export function reportPresentation(kind: ReportKind) {
  const suffix = kind === "feature" ? ".feature" : "";
  return {
    intro: `report.intro${suffix}`,
    sectionWhat: `report.section.what${suffix}`,
    sectionDescribe: `report.section.describe${suffix}`,
    describeHint: `report.describe.hint${suffix}`,
    doneThanks: `report.done.thanks${suffix}`,
    codeHint: `report.code.hint${suffix}`,
  };
}

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
