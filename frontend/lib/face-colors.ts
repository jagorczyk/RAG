export interface FaceColor {
  border: string;
  bg: string;
  text: string;
  inner: string;
}

/** Monochrome face boxes — black outer + white inner (mobile GraphRAG style). */
export const FACE_COLORS: FaceColor[] = [
  { border: "#000000", bg: "rgba(0, 0, 0, 0.08)", text: "#000000", inner: "#ffffff" },
  { border: "#000000", bg: "rgba(0, 0, 0, 0.08)", text: "#000000", inner: "#ffffff" },
  { border: "#000000", bg: "rgba(0, 0, 0, 0.08)", text: "#000000", inner: "#ffffff" },
  { border: "#000000", bg: "rgba(0, 0, 0, 0.08)", text: "#000000", inner: "#ffffff" },
];

export function getFaceColor(index: number): FaceColor {
  return FACE_COLORS[index % FACE_COLORS.length];
}
