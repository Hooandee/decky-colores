import { describe, expect, it } from "vitest";
import { reportPresentation } from "./logic";

describe("reportPresentation", () => {
  it("uses request-specific guidance for a feature request", () => {
    expect(reportPresentation?.("feature")).toEqual({
      intro: "report.intro.feature",
      sectionWhat: "report.section.what.feature",
      sectionDescribe: "report.section.describe.feature",
      describeHint: "report.describe.hint.feature",
      doneThanks: "report.done.thanks.feature",
      codeHint: "report.code.hint.feature",
    });
  });

  it("keeps the existing report copy for a problem", () => {
    expect(reportPresentation?.("bug")).toEqual({
      intro: "report.intro",
      sectionWhat: "report.section.what",
      sectionDescribe: "report.section.describe",
      describeHint: "report.describe.hint",
      doneThanks: "report.done.thanks",
      codeHint: "report.code.hint",
    });
  });
});
