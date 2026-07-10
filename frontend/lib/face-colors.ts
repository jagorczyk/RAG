export interface FaceColor {
  border: string;
  bg: string;
  text: string;
}

export const FACE_COLORS: FaceColor[] = [
  { border: "#2155e5", bg: "rgba(33, 85, 229, 0.18)", text: "#2155e5" },
  { border: "#e52b50", bg: "rgba(229, 43, 80, 0.18)", text: "#e52b50" },
  { border: "#0d9488", bg: "rgba(13, 148, 136, 0.18)", text: "#0d9488" },
  { border: "#d97706", bg: "rgba(217, 119, 6, 0.18)", text: "#d97706" },
  { border: "#7c3aed", bg: "rgba(124, 58, 237, 0.18)", text: "#7c3aed" },
  { border: "#db2777", bg: "rgba(219, 39, 119, 0.18)", text: "#db2777" },
  { border: "#059669", bg: "rgba(5, 150, 105, 0.18)", text: "#059669" },
  { border: "#ea580c", bg: "rgba(234, 88, 12, 0.18)", text: "#ea580c" },
];

export function getFaceColor(index: number): FaceColor {
  return FACE_COLORS[index % FACE_COLORS.length];
}
