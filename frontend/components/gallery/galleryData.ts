export type GalleryPhoto = {
  id: string;
  src: string;
  alt: string;
  title: string;
  /** Horizontal offset bias in the 3D stack (−1…1) */
  driftX: number;
  /** Vertical offset bias (−1…1) */
  driftY: number;
  /** Base rotateY in degrees */
  yaw: number;
  /** Base rotateX in degrees */
  pitch: number;
};

/** Decorative Cogniface landing photos — not user library sources. */
export const GALLERY_PHOTOS: GalleryPhoto[] = [
  {
    id: "g-01",
    src: "/collage/01.jpg",
    alt: "Portret w naturalnym świetle",
    title: "Światło dzienne",
    driftX: -0.35,
    driftY: -0.2,
    yaw: -8,
    pitch: 4,
  },
  {
    id: "g-02",
    src: "/collage/02.jpg",
    alt: "Scena rodzinna",
    title: "Razem",
    driftX: 0.4,
    driftY: 0.15,
    yaw: 10,
    pitch: -3,
  },
  {
    id: "g-03",
    src: "/collage/03.jpg",
    alt: "Krajobraz",
    title: "Horyzont",
    driftX: -0.15,
    driftY: 0.35,
    yaw: -4,
    pitch: 6,
  },
  {
    id: "g-04",
    src: "/collage/04.jpg",
    alt: "Moment codzienny",
    title: "Chwila",
    driftX: 0.25,
    driftY: -0.3,
    yaw: 7,
    pitch: -5,
  },
  {
    id: "g-05",
    src: "/collage/05.jpg",
    alt: "Portret z bliska",
    title: "Twarz",
    driftX: -0.45,
    driftY: 0.1,
    yaw: -11,
    pitch: 2,
  },
  {
    id: "g-06",
    src: "/collage/06.jpg",
    alt: "Wnętrze",
    title: "Dom",
    driftX: 0.1,
    driftY: -0.15,
    yaw: 5,
    pitch: 5,
  },
  {
    id: "g-07",
    src: "/collage/07.jpg",
    alt: "Wydarzenie",
    title: "Spotkanie",
    driftX: 0.5,
    driftY: 0.25,
    yaw: 12,
    pitch: -4,
  },
  {
    id: "g-08",
    src: "/collage/08.jpg",
    alt: "Archiwalne wspomnienie",
    title: "Archiwum",
    driftX: -0.2,
    driftY: -0.4,
    yaw: -6,
    pitch: 7,
  },
];
