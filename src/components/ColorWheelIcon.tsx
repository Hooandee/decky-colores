import { FC } from "react";

export const ColorWheelIcon: FC<{ size?: number | string }> = ({ size = "1em" }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    strokeWidth={4}
    aria-hidden="true"
  >
    <path d="M12 5 A7 7 0 0 1 18.06 8.5" stroke="#ff5252" />
    <path d="M18.06 8.5 A7 7 0 0 1 18.06 15.5" stroke="#ff9a3d" />
    <path d="M18.06 15.5 A7 7 0 0 1 12 19" stroke="#ffd23d" />
    <path d="M12 19 A7 7 0 0 1 5.94 15.5" stroke="#45c463" />
    <path d="M5.94 15.5 A7 7 0 0 1 5.94 8.5" stroke="#4d9aff" />
    <path d="M5.94 8.5 A7 7 0 0 1 12 5" stroke="#a96bff" />
  </svg>
);
