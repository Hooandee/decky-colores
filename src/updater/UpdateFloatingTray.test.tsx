import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import type { UpdateInfo } from "../api";
import { UpdateFloatingTray } from "./UpdateFloatingTray";

vi.mock("@decky/ui", () => ({
  Focusable: ({ children, ...props }: { children: React.ReactNode }) => <button {...props}>{children}</button>,
  showModal: vi.fn(),
}));
vi.mock("./UpdateModal", () => ({ UpdateModal: () => null }));

const info: UpdateInfo = { current: "0.20.0", latest: "0.21.0", has_update: true, notes: "", download_url: "", error: "" };

describe("UpdateFloatingTray", () => {
  it("shows both versions with the update and later actions", () => {
    const html = renderToStaticMarkup(<UpdateFloatingTray lang="es" info={info} status="idle" />);
    expect(html).toContain("Nueva versión disponible");
    expect(html).toContain("v0.20.0 → v0.21.0");
    expect(html).toContain('aria-label="Actualizar"');
    expect(html).toContain('aria-label="Más tarde"');
    expect(html).toContain('aria-live="polite"');
    expect(html).toContain("position:sticky");
  });

  it.each([null, { ...info, has_update: false }])("renders nothing without an available update: %j", (value) => {
    expect(renderToStaticMarkup(<UpdateFloatingTray lang="es" info={value} status="idle" />)).toBe("");
  });

  it.each(["installing", "done"] as const)("renders nothing while %s", (status) => {
    expect(renderToStaticMarkup(<UpdateFloatingTray lang="es" info={info} status={status} />)).toBe("");
  });

  it("stays available after a failed install", () => {
    expect(renderToStaticMarkup(<UpdateFloatingTray lang="en" info={info} status="error" />)).toContain("New version available");
  });
});
