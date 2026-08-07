export interface GameOverview {
  appid: string | number;
  display_name?: string;
  app_type?: number;
}

const APP_TYPE_SHORTCUT = 1073741824;
const NON_STEAM_APPID_MIN = 2147483648;

function normalizeGameName(name: string): string {
  return name.trim().toLowerCase().replace(/\s+/g, " ");
}

export function stableGameKey(app: GameOverview): string {
  const raw = String(app.appid);
  const numeric = Number(app.appid);
  const isShortcut =
    app.app_type === APP_TYPE_SHORTCUT ||
    (Number.isFinite(numeric) && numeric >= NON_STEAM_APPID_MIN);
  if (isShortcut) {
    const name = app.display_name ? normalizeGameName(app.display_name) : "";
    if (name) return `ns:${name}`;
  }
  return raw;
}
