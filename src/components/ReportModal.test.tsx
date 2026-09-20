import { createElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";

type FocusableProps = {
  children?: ReactNode;
  onActivate?: () => void;
  onClick?: () => void;
  role?: string;
  preferredFocus?: boolean;
  "aria-label"?: string;
  "aria-checked"?: boolean;
};

const mocks = vi.hoisted(() => ({
  modal: null as ReactNode | null,
  focusables: [] as FocusableProps[],
}));

vi.mock("@decky/ui", async () => {
  const React = await import("react");
  return {
    DialogButton: ({ children, ...props }: { children?: ReactNode }) =>
      React.createElement("button", props, children),
    Focusable: ({ children, onActivate, onClick, ...props }: FocusableProps) => {
      mocks.focusables.push({ children, onActivate, onClick, ...props });
      return React.createElement("div", props, children);
    },
    ModalRoot: ({ children }: { children?: ReactNode }) => children,
    showModal: (node: ReactNode) => { mocks.modal = node; },
    TextField: (props: object) => React.createElement("input", props),
  };
});

vi.mock("../api", () => ({
  submitReport: vi.fn(),
}));

vi.mock("../i18n", () => ({
  I18nProvider: ({ children }: { children?: ReactNode }) => children,
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("./FocusRoot", () => ({
  FocusRoot: ({ children }: { children?: ReactNode }) => children,
}));

import {
  openReportModal,
  reportFocusTarget,
  selectionChipA11y,
  selectionChipStyle,
} from "./ReportModal";

const device = {
  name: "MSI Claw",
  product: "Claw A1M",
  board: "MS-1T41",
};

describe("ReportModal request type", () => {
  afterEach(() => {
    mocks.modal = null;
    mocks.focusables.length = 0;
    vi.unstubAllGlobals();
  });

  it("starts with an explicit report type choice before showing the form", () => {
    vi.stubGlobal("window", {});
    openReportModal(device);
    const html = renderToStaticMarkup(createElement("div", null, mocks.modal));

    const problem = mocks.focusables.find(
      (props) => props["aria-label"] === "report.kind.bug",
    );
    const feature = mocks.focusables.find(
      (props) => props["aria-label"] === "report.kind.feature",
    );

    expect(problem).toMatchObject({
      role: "radio",
      "aria-checked": false,
      preferredFocus: true,
    });
    expect(feature).toMatchObject({ role: "radio", "aria-checked": false });
    expect(html).toContain("MSI Claw");
    expect(
      mocks.focusables.some((props) => props["aria-label"]?.startsWith("report.cat.")),
    ).toBe(false);
  });

  it("draws a distinct gamepad focus state for category chips", () => {
    expect(selectionChipStyle(false, true).boxShadow).toContain("2px");
    expect(selectionChipStyle(false, true).boxShadow).not.toBe(
      selectionChipStyle(false, false).boxShadow,
    );
  });

  it("hands focus to the first category after choosing a report type", () => {
    const category = {} as HTMLElement;
    const root = { querySelector: vi.fn(() => category) };

    expect(reportFocusTarget(root, "form", false, "bug")).toBe(category);
    expect(root.querySelector).toHaveBeenCalledWith('[data-report-category="true"]');
  });

  it("hands focus to the primary action after submission finishes", () => {
    const action = {} as HTMLElement;
    const root = { querySelector: vi.fn(() => action) };

    expect(reportFocusTarget(root, "done", false, "bug")).toBe(action);
    expect(reportFocusTarget(root, "error", false, "bug")).toBe(action);
    expect(root.querySelector).toHaveBeenCalledWith(
      '[data-report-primary-action="true"]',
    );
  });

  it("exposes report categories as checked or unchecked checkboxes", () => {
    expect(selectionChipA11y(true)).toEqual({ role: "checkbox", "aria-checked": true });
    expect(selectionChipA11y(false)).toEqual({ role: "checkbox", "aria-checked": false });
  });
});
