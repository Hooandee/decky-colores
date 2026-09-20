import { createElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

vi.mock("@decky/ui", async () => {
  const React = await import("react");
  return {
    Focusable: ({ children, onActivate: _onActivate, ...props }: {
      children?: ReactNode;
      onActivate?: () => void;
    }) => React.createElement("div", props, children),
  };
});

vi.mock("../i18n", () => ({
  useI18n: () => ({
    t: (key: string) => ({
      "profiles.global": "Global",
      "profiles.gameShort": "Juego",
      "profiles.followingGlobal": "Sigue el perfil global",
      "profiles.useOwn": "Usar perfil propio",
    })[key] ?? key,
  }),
}));

import { ProfileScopeSelector } from "./ProfileScopeSelector";

describe("ProfileScopeSelector", () => {
  it("uses the same short Global/Juego labels while preserving the game identity", () => {
    const html = renderToStaticMarkup(createElement(ProfileScopeSelector, {
      scope: "game",
      runningApp: { key: "steam:413150", liveAppId: 413150, name: "Stardew Valley" },
      onSelect: vi.fn(),
    }));

    expect(html).toContain("Global");
    expect(html).toContain("Juego");
    expect(html).not.toContain("Juego: Stardew Valley");
    expect(html).toContain('title="Stardew Valley"');
    expect(html).toContain('role="radiogroup"');
    expect(html.match(/role="radio"/g)).toHaveLength(2);
    expect(html).toContain('aria-checked="true"');
    expect(html).not.toContain("Sigue el perfil global");
    expect(html).not.toContain("Usar perfil propio");
  });

  it("blocks both choices while the active profile is changing", () => {
    const html = renderToStaticMarkup(createElement(ProfileScopeSelector, {
      scope: "global",
      runningApp: { key: "steam:413150", liveAppId: 413150, name: "Stardew Valley" },
      disabled: true,
      onSelect: vi.fn(),
    }));

    expect(html).toContain('aria-busy="true"');
    expect(html.match(/aria-disabled="true"/g)).toHaveLength(2);
  });
});
