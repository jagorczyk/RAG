"use client";

import { useCallback, useLayoutEffect, useRef, useState } from "react";
import { getFaceColor } from "@/lib/face-colors";

export interface AnnotatedFace {
  id: string;
  bbox: number[];
  colorIndex: number;
}

interface FaceAnnotatedImageProps {
  src: string;
  alt: string;
  faces: AnnotatedFace[];
}

interface RenderMetrics {
  offsetX: number;
  offsetY: number;
  renderW: number;
  renderH: number;
  naturalW: number;
  naturalH: number;
}

export function FaceAnnotatedImage({ src, alt, faces }: FaceAnnotatedImageProps) {
  const imgRef = useRef<HTMLImageElement>(null);
  const [metrics, setMetrics] = useState<RenderMetrics | null>(null);

  const updateMetrics = useCallback(() => {
    const img = imgRef.current;
    if (!img || !img.naturalWidth) {
      return;
    }

    const rect = img.getBoundingClientRect();
    const naturalRatio = img.naturalWidth / img.naturalHeight;
    const displayRatio = rect.width / rect.height;

    let renderW: number;
    let renderH: number;
    let offsetX: number;
    let offsetY: number;

    if (naturalRatio > displayRatio) {
      renderW = rect.width;
      renderH = rect.width / naturalRatio;
      offsetX = 0;
      offsetY = (rect.height - renderH) / 2;
    } else {
      renderH = rect.height;
      renderW = rect.height * naturalRatio;
      offsetX = (rect.width - renderW) / 2;
      offsetY = 0;
    }

    setMetrics({
      offsetX,
      offsetY,
      renderW,
      renderH,
      naturalW: img.naturalWidth,
      naturalH: img.naturalHeight,
    });
  }, []);

  useLayoutEffect(() => {
    updateMetrics();
    window.addEventListener("resize", updateMetrics);
    return () => window.removeEventListener("resize", updateMetrics);
  }, [updateMetrics, src]);

  return (
    <div className="relative mx-auto w-fit max-w-full">
      <img
        ref={imgRef}
        src={src}
        alt={alt}
        onLoad={updateMetrics}
        className="mx-auto block max-h-[70vh] rounded-[8px] object-contain"
      />
      {metrics &&
        faces.map((face) => {
          if (face.bbox.length < 4) {
            return null;
          }

          const [x1, y1, x2, y2] = face.bbox;
          const color = getFaceColor(face.colorIndex);
          const left = metrics.offsetX + (x1 / metrics.naturalW) * metrics.renderW;
          const top = metrics.offsetY + (y1 / metrics.naturalH) * metrics.renderH;
          const width = ((x2 - x1) / metrics.naturalW) * metrics.renderW;
          const height = ((y2 - y1) / metrics.naturalH) * metrics.renderH;

          return (
            <div
              key={face.id}
              className="pointer-events-none absolute"
              style={{
                left,
                top,
                width,
                height,
                border: `3px solid ${color.border}`,
                backgroundColor: color.bg,
              }}
              aria-hidden="true"
            />
          );
        })}
    </div>
  );
}
