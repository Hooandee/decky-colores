type SteamUser = Partial<NonNullable<NonNullable<typeof SteamClient>["User"]>>;
const PREPARATION_COMPLETE = 1;

export function startSuspendPreparation(
  prepare: () => Promise<void>,
  steamUser: SteamUser | undefined = SteamClient?.User,
): () => void {
  const registration = steamUser?.RegisterForPrepareForSystemSuspendProgress?.(
    ({ state }) => {
      if (state === PREPARATION_COMPLETE) void prepare().catch(() => undefined);
    },
  );
  return () => registration?.unregister();
}
