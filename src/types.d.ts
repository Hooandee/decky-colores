declare module "*.png";
declare module "*.svg";

declare const SteamClient:
  | {
      GameSessions?: {
        RegisterForAppLifetimeNotifications?: (
          callback: () => void,
        ) => { unregister: () => void } | undefined;
      };
    }
  | undefined;
