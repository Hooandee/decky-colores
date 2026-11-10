type SteamDownloads = Partial<NonNullable<NonNullable<typeof SteamClient>["Downloads"]>>;

type Overview = {
  paused?: boolean;
  update_state?: string;
  update_is_upload?: boolean;
};

export function isSteamDownloading(overview: Overview): boolean {
  if (overview.paused || overview.update_is_upload) return false;
  return overview.update_state === "Starting" || overview.update_state === "Updating";
}

export function watchSteamDownloads(
  onChange: (active: boolean) => Promise<void>,
  downloads: SteamDownloads | undefined = typeof SteamClient === "undefined" ? undefined : SteamClient.Downloads,
): () => void {
  let last: boolean | undefined;
  const registration = downloads?.RegisterForDownloadOverview?.((overview) => {
    const active = isSteamDownloading(overview);
    if (active === last) return;
    last = active;
    void onChange(active).catch(() => {
      last = undefined;
    });
  });
  return () => {
    registration?.unregister();
    if (last) void onChange(false).catch(() => undefined);
  };
}
