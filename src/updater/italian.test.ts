import { describe, expect, it, vi } from "vitest";

vi.mock("@decky/ui", () => ({
  DialogButton: () => null,
  Focusable: () => null,
  ModalRoot: () => null,
  showModal: vi.fn(),
}));

vi.mock("@decky/api", () => ({
  callable: vi.fn(() => vi.fn()),
  toaster: { toast: vi.fn() },
}));

import { getUpdatePanelStrings } from "./UpdatePanel";
import { getUpdateModalStrings } from "./UpdateModal";
import { updateAvailableTitle } from "./useUpdate";

describe("Italian updater copy", () => {
  it("localizes the settings panel", () => {
    expect(getUpdatePanelStrings("it")).toEqual({
      version: "Versione",
      latest: "(più recente)",
      newPrefix: "disponibile",
      checking: "verifica in corso…",
      check: "Cerca aggiornamenti",
      update: "Scopri le novità e installa",
      error: "Verifica non riuscita. Controlla la connessione.",
    });
  });

  it("localizes the update modal", () => {
    expect(getUpdateModalStrings("it")).toEqual({
      title: "Novità",
      noNotes: "Nessuna nota per questa versione.",
      install: "Installa l'aggiornamento",
      installing: "Installazione in corso…",
      installed: "Aggiornamento installato.",
      restartNote: "Riavvia Decky per applicare l'aggiornamento.",
      restart: "Riavvia Decky",
      failed: "Installazione non riuscita. Riprova.",
    });
  });

  it("localizes the update-available toast", () => {
    expect(updateAvailableTitle("it")).toBe("Aggiornamento disponibile");
  });
});
