import { createElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";

type FocusableProps = {
  children?: ReactNode;
  onActivate?: () => void;
  onClick?: () => void;
  role?: string;
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
  getState: vi.fn(() => new Promise(() => {})),
  submitReport: vi.fn(),
}));

vi.mock("../i18n", () => ({
  I18nProvider: ({ children }: { children?: ReactNode }) => children,
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("./FocusRoot", () => ({
  FocusRoot: ({ children }: { children?: ReactNode }) => children,
}));

import { openReportModal } from "./ReportModal";

describe("ReportModal request type", () => {
  afterEach(() => {
    mocks.modal = null;
    mocks.focusables.length = 0;
    vi.unstubAllGlobals();
  });

  it("offers problem and feature choices with problem selected by default", () => {
    vi.stubGlobal("window", {});
    openReportModal();
    renderToStaticMarkup(createElement("div", null, mocks.modal));

    const problem = mocks.focusables.find(
      (props) => props["aria-label"] === "report.kind.bug",
    );
    const feature = mocks.focusables.find(
      (props) => props["aria-label"] === "report.kind.feature",
    );

    expect(problem).toMatchObject({ role: "radio", "aria-checked": true });
    expect(feature).toMatchObject({ role: "radio", "aria-checked": false });
  });
});
