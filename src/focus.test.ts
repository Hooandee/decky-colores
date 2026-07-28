import { describe, it, expect } from "vitest";
import {
  buildFocusCss,
  ensureFocusStyles,
  FOCUS_STYLE_ID,
  COLORES_ROOT,
  COLORES_TABSTRIP,
  focusAfterNavigation,
} from "./focus";

describe("buildFocusCss", () => {
  const css = buildFocusCss();

  it("targets Steam's live gpfocus class, scoped to our root", () => {
    expect(css).toContain(`.${COLORES_ROOT} .gpfocus`);
  });

  it("colours the ring from the accent variable, with the blue default fallback", () => {
    expect(css).toContain("var(--colores-accent-rgb");
    expect(css).toContain("91,140,255");
    expect(css).toContain("box-shadow");
  });

  it("rounds the ring so square controls don't get sharp corners", () => {
    expect(css).toContain("border-radius");
  });

  it("uses !important so it wins over the elements' inline box-shadow", () => {
    expect(css).toContain("!important");
  });

  it("hides the native scrollbar on the horizontal tab strip", () => {
    expect(css).toContain(`.${COLORES_TABSTRIP} { scrollbar-width: none;`);
    expect(css).toContain(`.${COLORES_TABSTRIP}::-webkit-scrollbar`);
  });
});

function fakeDoc() {
  const store: Record<string, unknown> = {};
  const head = {
    children: [] as unknown[],
    appendChild(el: unknown) {
      this.children.push(el);
      const withId = el as { id?: string };
      if (withId.id) store[withId.id] = el;
    },
  };
  return {
    appended: () => head.children.length,
    doc: {
      getElementById: (id: string) => (store[id] as object) ?? null,
      createElement: (_tag: string) => ({ id: "", textContent: "" }),
      head,
    },
  };
}

describe("ensureFocusStyles", () => {
  it("injects the stylesheet once", () => {
    const { doc, appended } = fakeDoc();
    ensureFocusStyles(doc as unknown as Document);
    expect(appended()).toBe(1);
  });

  it("is idempotent — a second call adds nothing", () => {
    const { doc, appended } = fakeDoc();
    ensureFocusStyles(doc as unknown as Document);
    ensureFocusStyles(doc as unknown as Document);
    expect(appended()).toBe(1);
  });

  it("tags the injected element with the stable id", () => {
    const { doc } = fakeDoc();
    ensureFocusStyles(doc as unknown as Document);
    expect(doc.getElementById(FOCUS_STYLE_ID)).not.toBeNull();
  });

  it("never throws when the document surface is unusable", () => {
    expect(() => ensureFocusStyles({} as unknown as Document)).not.toThrow();
  });
});

describe("focusAfterNavigation", () => {
  it("reasserts the active focus after Steam restores the previous controller focus", () => {
    let focused = "previous";
    let scheduled: FrameRequestCallback | undefined;
    const element = {
      isConnected: true,
      ownerDocument: { hasFocus: () => true },
      focus: () => {
        focused = "active";
      },
    } as unknown as HTMLElement;

    focusAfterNavigation(
      element,
      (callback) => {
        scheduled = callback;
        return 1;
      },
      () => undefined,
    );
    focused = "previous";
    scheduled?.(0);

    expect(focused).toBe("active");
  });

  it("does not steal focus when the QAM document is inactive", () => {
    let focusCalls = 0;
    let scheduled: FrameRequestCallback | undefined;
    const element = {
      isConnected: true,
      ownerDocument: { hasFocus: () => false },
      focus: () => {
        focusCalls += 1;
      },
    } as unknown as HTMLElement;

    focusAfterNavigation(
      element,
      (callback) => {
        scheduled = callback;
        return 1;
      },
      () => undefined,
    );
    scheduled?.(0);

    expect(focusCalls).toBe(0);
  });
});
