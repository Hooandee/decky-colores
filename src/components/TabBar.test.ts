import { createElement, type ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  focusAfterNavigation: vi.fn(),
}));

vi.mock("@decky/ui", () => ({
  Focusable: ({ children, ...props }: { children?: ReactNode }) =>
    createElement("div", props, children),
}));

vi.mock("../focus", () => ({
  COLORES_TABSTRIP: "colores-tabstrip",
  focusAfterNavigation: mocks.focusAfterNavigation,
}));

import { focusActiveTab, tabWindow } from "./TabBar";

type Item = { id: string };

const items: Item[] = [{ id: "solid" }, { id: "gradient" }, { id: "settings" }];

describe("tabWindow", () => {
  it("wraps the neighboring tabs around both ends", () => {
    expect(tabWindow(items, "solid")).toEqual({
      previous: { id: "settings" },
      active: { id: "solid" },
      next: { id: "gradient" },
    });
    expect(tabWindow(items, "settings")).toEqual({
      previous: { id: "gradient" },
      active: { id: "settings" },
      next: { id: "solid" },
    });
  });

  it("falls back to the first tab and handles an empty list", () => {
    expect(tabWindow(items, "missing")?.active).toEqual({ id: "solid" });
    expect(tabWindow([], "solid")).toBeNull();
  });

  it("moves focus to the active section after shoulder navigation", () => {
    const active = {} as HTMLElement;
    const root = { querySelector: vi.fn(() => active) };
    focusActiveTab(root, "gradient");

    expect(root.querySelector).toHaveBeenCalledWith('[data-section-id="gradient"]');
    expect(mocks.focusAfterNavigation).toHaveBeenCalledWith(active);
  });
});
