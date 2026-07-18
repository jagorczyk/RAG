"use client";

import { useCallback, useLayoutEffect, useRef, useState } from "react";
import { getFaceColor } from "@/lib/face-colors";

export interface AnnotatedFace {
  id: string;
  bbox: number[];
  colorIndex: number;
  label?: string;
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
  }, [updateMetrics, src, faces]);

  return (
    <div className="relative mx-auto w-fit max-w-full">
      <img
        ref={imgRef}
        src={src}
        alt={alt}
        onLoad={updateMetrics}
        className="mx-auto block max-h-[70vh] rounded-2xl object-contain"
      />
      {metrics &&
        faces.map((face) => {
          if (face.bbox.length < 4) {
            return null;
          }

          const [rawX1, rawY1, rawX2, rawY2] = face.bbox;
          // Keep stale/invalid data from drawing outside the actual image.
          const x1 = Math.max(0, Math.min(rawX1, metrics.naturalW));
          const y1 = Math.max(0, Math.min(rawY1, metrics.naturalH));
          const x2 = Math.max(x1, Math.min(rawX2, metrics.naturalW));
          const y2 = Math.max(y1, Math.min(rawY2, metrics.naturalH));
          const color = getFaceColor(face.colorIndex);
          const left = metrics.offsetX + (x1 / metrics.naturalW) * metrics.renderW;
          const top = metrics.offsetY + (y1 / metrics.naturalH) * metrics.renderH;
          const width = ((x2 - x1) / metrics.naturalW) * metrics.renderW;
          const height = ((y2 - y1) / metrics.naturalH) * metrics.renderH;

          return (
            <div
              key={face.id}
              className="pointer-events-none absolute z-10 box-border rounded-sm p-0.5"
              style={{
                left,
                top,
                width,
                height,
                border: `3px solid ${color.border}`,
              }}
              aria-hidden="true"
            >
              {face.label && (
                <span
                  className="absolute left-0 top-0 flex min-h-5 min-w-5 -translate-x-[3px] -translate-y-[3px] items-center justify-center rounded-br-md px-1 text-[11px] font-extrabold leading-5 shadow-sm"
                  style={{
                    backgroundColor: color.border,
                    color: color.text,
                    boxShadow: "0 0 0 1px rgba(255, 255, 255, 0.95)",
                  }}
                >
                  {face.label}
                </span>
              )}
              <div
                className="h-full w-full rounded-[1px]"
                style={{ border: `1px solid ${color.inner ?? "#fff"}` }}
              />
            </div>
          );
        })}
    </div>
  );
}
