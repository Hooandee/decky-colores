export interface CalibrationSteps {
  yellow: number;
  orange: number;
  white: number;
}

export type CalibrationStepId = keyof CalibrationSteps;

export const NEUTRAL_CALIBRATION: CalibrationSteps = { yellow: 0, orange: 0, white: 0 };

export const CALIBRATION_STEPS: readonly { id: CalibrationStepId; sample: number[]; limit: number }[] = [
  { id: "yellow", sample: [255, 255, 0], limit: 60 },
  { id: "orange", sample: [255, 128, 0], limit: 100 },
  { id: "white", sample: [255, 255, 255], limit: 60 },
];
