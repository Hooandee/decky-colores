import { describe, it, expect } from "vitest";
import { orderIds, visibleIds, move, toggle, coerceLayout, EMPTY_LAYOUT } from "./layout";

describe("coerceLayout", () => {
  it("returns the empty layout for non-object input", () => {
    expect(coerceLayout(null)).toEqual(EMPTY_LAYOUT);
    expect(coerceLayout(5)).toEqual(EMPTY_LAYOUT);
    expect(coerceLayout("x")).toEqual(EMPTY_LAYOUT);
    expect(coerceLayout([])).toEqual(EMPTY_LAYOUT);
  });

  it("keeps a well-formed layout", () => {
    const good = { version: 1 as const, tabs: { order: ["a"], hidden: ["b"] } };
    expect(coerceLayout(good)).toEqual(good);
  });

  it("coerces wrong-typed fields to empty arrays (never throws downstream)", () => {
    expect(coerceLayout({ tabs: { order: 5, hidden: "x" } })).toEqual(EMPTY_LAYOUT);
    expect(coerceLayout({ tabs: { order: ["a", 3, null], hidden: [7, "b"] } })).toEqual({
      version: 1,
      tabs: { order: ["a"], hidden: ["b"] },
    });
  });

  it("defaults tabs when the field is missing", () => {
    expect(coerceLayout({})).toEqual(EMPTY_LAYOUT);
  });
});

describe("orderIds", () => {
  const defaults = ["a", "b", "c"];

  it("returns defaults when no saved order", () => {
    expect(orderIds(defaults, undefined)).toEqual(["a", "b", "c"]);
  });

  it("applies the saved order", () => {
    expect(orderIds(defaults, ["c", "a", "b"])).toEqual(["c", "a", "b"]);
  });

  it("drops stale ids no longer in defaults", () => {
    expect(orderIds(defaults, ["c", "z", "a"])).toEqual(["c", "a", "b"]);
  });

  it("appends new defaults the saved order never saw (visible, at the end)", () => {
    expect(orderIds(defaults, ["b"])).toEqual(["b", "a", "c"]);
  });

  it("dedupes a corrupt saved order", () => {
    expect(orderIds(defaults, ["a", "a", "b"])).toEqual(["a", "b", "c"]);
  });
});

describe("visibleIds", () => {
  const defaults = ["a", "b", "c"];

  it("hides ids in the hidden set", () => {
    expect(visibleIds(defaults, { order: [], hidden: ["b"] })).toEqual(["a", "c"]);
  });

  it("keeps a pinned id visible even when marked hidden", () => {
    expect(visibleIds(defaults, { order: [], hidden: ["a", "b", "c"] }, ["c"])).toEqual(["c"]);
  });

  it("respects order and hidden together", () => {
    expect(visibleIds(defaults, { order: ["c", "b", "a"], hidden: ["b"] })).toEqual(["c", "a"]);
  });
});

describe("move", () => {
  it("moves up and down", () => {
    expect(move(["a", "b", "c"], "b", -1)).toEqual(["b", "a", "c"]);
    expect(move(["a", "b", "c"], "b", 1)).toEqual(["a", "c", "b"]);
  });

  it("is a no-op at the edges", () => {
    expect(move(["a", "b"], "a", -1)).toEqual(["a", "b"]);
    expect(move(["a", "b"], "b", 1)).toEqual(["a", "b"]);
  });

  it("is a no-op for an unknown id", () => {
    expect(move(["a", "b"], "z", 1)).toEqual(["a", "b"]);
  });

  it("does not mutate the input", () => {
    const list = ["a", "b"];
    move(list, "a", 1);
    expect(list).toEqual(["a", "b"]);
  });
});

describe("toggle", () => {
  it("adds when absent, removes when present", () => {
    expect(toggle(["a"], "b")).toEqual(["a", "b"]);
    expect(toggle(["a", "b"], "b")).toEqual(["a"]);
  });
});
