export interface FaceColor {
  border: string;
  bg: string;
  text: string;
  inner: string;
}

/** Distinct, saturated colors remain readable over both light and dark photos. */
export const FACE_COLORS: FaceColor[] = [
  { border: "#2155e5", bg: "rgba(33, 85, 229, 0.18)", text: "#ffffff", inner: "#ffffff" },
  { border: "#e52b50", bg: "rgba(229, 43, 80, 0.18)", text: "#ffffff", inner: "#ffffff" },
  { border: "#0d9488", bg: "rgba(13, 148, 136, 0.18)", text: "#ffffff", inner: "#ffffff" },
  { border: "#d97706", bg: "rgba(217, 119, 6, 0.18)", text: "#ffffff", inner: "#ffffff" },
  { border: "#7c3aed", bg: "rgba(124, 58, 237, 0.18)", text: "#ffffff", inner: "#ffffff" },
  { border: "#db2777", bg: "rgba(219, 39, 119, 0.18)", text: "#ffffff", inner: "#ffffff" },
  { border: "#059669", bg: "rgba(5, 150, 105, 0.18)", text: "#ffffff", inner: "#ffffff" },
  { border: "#ea580c", bg: "rgba(234, 88, 12, 0.18)", text: "#ffffff", inner: "#ffffff" },
];

export function getFaceColor(index: number): FaceColor {
  return FACE_COLORS[index % FACE_COLORS.length];
}
