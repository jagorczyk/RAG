"use client";

import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type WheelEvent as ReactWheelEvent,
} from "react";
import { useRouter } from "next/navigation";
import { Minus, Plus, RotateCcw } from "lucide-react";
import type { PersonGraphEdge, PersonGraphNode } from "@/lib/knowledge-api";

interface PersonRelationGraphProps {
  nodes: PersonGraphNode[];
  edges: PersonGraphEdge[];
}

interface SimNode {
  id: string;
  displayName: string;
  photoCount: number;
  x: number;
  y: number;
  vx: number;
  vy: number;
}

interface SimEdge {
  sourceId: string;
  targetId: string;
  relation: string;
  weight: number;
  kind: string;
}

interface ViewTransform {
  scale: number;
  tx: number;
  ty: number;
}

const NODE_RADIUS = 30;
const REPULSION = 5200;
const SPRING = 0.022;
const SPRING_LENGTH = 180;
const SPRING_LENGTH_FOCUS = 280;
const CENTER_PULL = 0.006;
const DAMPING = 0.86;
const MAX_SPEED = 9;
const MIN_SCALE = 0.45;
const MAX_SCALE = 3.5;
const CLICK_MOVE_THRESHOLD = 6;

export function PersonRelationGraph({ nodes, edges }: PersonRelationGraphProps) {
  const router = useRouter();
  const containerRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const simNodesRef = useRef<SimNode[]>([]);
  const simEdgesRef = useRef<SimEdge[]>([]);
  const dragIdRef = useRef<string | null>(null);
  const dragOffsetRef = useRef({ x: 0, y: 0 });
  const dragMovedRef = useRef(false);
  const dragStartClientRef = useRef({ x: 0, y: 0 });
  const panningRef = useRef(false);
  const panStartRef = useRef({ x: 0, y: 0, tx: 0, ty: 0 });
  const sizeRef = useRef({ width: 800, height: 560 });
  const frameRef = useRef<number | null>(null);
  const selectedIdRef = useRef<string | null>(null);
  const viewRef = useRef<ViewTransform>({ scale: 1, tx: 0, ty: 0 });

  const [renderTick, setRenderTick] = useState(0);
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [view, setView] = useState<ViewTransform>({ scale: 1, tx: 0, ty: 0 });

  selectedIdRef.current = selectedId;
  viewRef.current = view;

  const nodeIds = useMemo(() => nodes.map((n) => n.id).join(","), [nodes]);
  const edgeKey = useMemo(
    () => edges.map((e) => `${e.sourceId}-${e.targetId}-${e.kind}-${e.relation}`).join("|"),
    [edges]
  );

  const neighborIds = useMemo(() => {
    if (!selectedId) return new Set<string>();
    const set = new Set<string>([selectedId]);
    for (const edge of edges) {
      if (edge.sourceId === selectedId) set.add(edge.targetId);
      if (edge.targetId === selectedId) set.add(edge.sourceId);
    }
    return set;
  }, [selectedId, edges]);

  useEffect(() => {
    const width = sizeRef.current.width;
    const height = sizeRef.current.height;
    const cx = width / 2;
    const cy = height / 2;
    const radius = Math.min(width, height) * 0.3;

    simNodesRef.current = nodes.map((node, index) => {
      const angle = (index / Math.max(nodes.length, 1)) * Math.PI * 2;
      return {
        id: node.id,
        displayName: node.displayName,
        photoCount: node.photoCount,
        x: cx + Math.cos(angle) * radius + (Math.random() - 0.5) * 24,
        y: cy + Math.sin(angle) * radius + (Math.random() - 0.5) * 24,
        vx: 0,
        vy: 0,
      };
    });
    simEdgesRef.current = edges.map((edge) => ({
      sourceId: edge.sourceId,
      targetId: edge.targetId,
      relation: edge.relation,
      weight: edge.weight,
      kind: edge.kind,
    }));
    setSelectedId(null);
    setView({ scale: 1, tx: 0, ty: 0 });
    setRenderTick((t) => t + 1);
  }, [nodeIds, edgeKey, nodes, edges]);

  useEffect(() => {
    const measure = () => {
      const el = containerRef.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      sizeRef.current = {
        width: Math.max(320, rect.width),
        height: Math.max(420, rect.height),
      };
    };
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, []);

  useEffect(() => {
    if (simNodesRef.current.length === 0) {
      return;
    }

    const step = () => {
      const simNodes = simNodesRef.current;
      const simEdges = simEdgesRef.current;
      const { width, height } = sizeRef.current;
      const cx = width / 2;
      const cy = height / 2;
      const byId = new Map(simNodes.map((n) => [n.id, n]));
      const focusId = selectedIdRef.current;

      for (let i = 0; i < simNodes.length; i++) {
        for (let j = i + 1; j < simNodes.length; j++) {
          const a = simNodes[i];
          const b = simNodes[j];
          let dx = b.x - a.x;
          let dy = b.y - a.y;
          let distSq = dx * dx + dy * dy;
          if (distSq < 1) {
            dx = (Math.random() - 0.5) * 2;
            dy = (Math.random() - 0.5) * 2;
            distSq = dx * dx + dy * dy;
          }
          const dist = Math.sqrt(distSq);
          let force = REPULSION / distSq;
          // When focused, push neighbors of the selected person farther apart
          // so their relation lines become longer and easier to read.
          if (
            focusId &&
            (a.id === focusId || b.id === focusId) &&
            isNeighborEdge(focusId, a.id, b.id, simEdges)
          ) {
            force *= 1.55;
          }
          const fx = (dx / dist) * force;
          const fy = (dy / dist) * force;
          if (dragIdRef.current !== a.id) {
            a.vx -= fx;
            a.vy -= fy;
          }
          if (dragIdRef.current !== b.id) {
            b.vx += fx;
            b.vy += fy;
          }
        }
      }

      for (const edge of simEdges) {
        const a = byId.get(edge.sourceId);
        const b = byId.get(edge.targetId);
        if (!a || !b) continue;
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
        const focused =
          focusId != null && (edge.sourceId === focusId || edge.targetId === focusId);
        const baseLen = focused ? SPRING_LENGTH_FOCUS : SPRING_LENGTH;
        const targetLen = baseLen + Math.min(edge.weight, 8) * 10;
        const spring = focused ? SPRING * 1.15 : SPRING;
        const force = (dist - targetLen) * spring;
        const fx = (dx / dist) * force;
        const fy = (dy / dist) * force;
        if (dragIdRef.current !== a.id) {
          a.vx += fx;
          a.vy += fy;
        }
        if (dragIdRef.current !== b.id) {
          b.vx -= fx;
          b.vy -= fy;
        }
      }

      for (const node of simNodes) {
        if (dragIdRef.current === node.id) {
          node.vx = 0;
          node.vy = 0;
          continue;
        }
        // Soft center pull; weaker when someone is selected so focus can expand.
        const pull = focusId ? CENTER_PULL * 0.45 : CENTER_PULL;
        node.vx += (cx - node.x) * pull;
        node.vy += (cy - node.y) * pull;
        node.vx *= DAMPING;
        node.vy *= DAMPING;
        const speed = Math.sqrt(node.vx * node.vx + node.vy * node.vy);
        if (speed > MAX_SPEED) {
          node.vx = (node.vx / speed) * MAX_SPEED;
          node.vy = (node.vy / speed) * MAX_SPEED;
        }
        node.x += node.vx;
        node.y += node.vy;
        const pad = NODE_RADIUS + 8;
        node.x = Math.min(width - pad, Math.max(pad, node.x));
        node.y = Math.min(height - pad, Math.max(pad, node.y));
      }

      setRenderTick((t) => t + 1);
      frameRef.current = requestAnimationFrame(step);
    };

    frameRef.current = requestAnimationFrame(step);
    return () => {
      if (frameRef.current != null) {
        cancelAnimationFrame(frameRef.current);
      }
    };
  }, [nodeIds, edgeKey]);

  const applyZoom = (nextScale: number, pivotClientX?: number, pivotClientY?: number) => {
    const svg = svgRef.current;
    const clamped = Math.min(MAX_SCALE, Math.max(MIN_SCALE, nextScale));
    const prev = viewRef.current;
    const { width, height } = sizeRef.current;

    if (!svg || pivotClientX == null || pivotClientY == null) {
      const cx = width / 2;
      const cy = height / 2;
      const worldX = (cx - prev.tx) / prev.scale;
      const worldY = (cy - prev.ty) / prev.scale;
      setView({
        scale: clamped,
        tx: cx - worldX * clamped,
        ty: cy - worldY * clamped,
      });
      return;
    }

    const rect = svg.getBoundingClientRect();
    const localX = ((pivotClientX - rect.left) / rect.width) * width;
    const localY = ((pivotClientY - rect.top) / rect.height) * height;
    const worldX = (localX - prev.tx) / prev.scale;
    const worldY = (localY - prev.ty) / prev.scale;
    setView({
      scale: clamped,
      tx: localX - worldX * clamped,
      ty: localY - worldY * clamped,
    });
  };

  const onWheel = (event: ReactWheelEvent) => {
    event.preventDefault();
    const factor = event.deltaY > 0 ? 0.9 : 1.11;
    applyZoom(viewRef.current.scale * factor, event.clientX, event.clientY);
  };

  const onBackgroundPointerDown = (event: ReactPointerEvent) => {
    if (event.target !== svgRef.current && (event.target as Element).closest("[data-node]")) {
      return;
    }
    panningRef.current = true;
    panStartRef.current = {
      x: event.clientX,
      y: event.clientY,
      tx: viewRef.current.tx,
      ty: viewRef.current.ty,
    };
    (event.currentTarget as Element).setPointerCapture?.(event.pointerId);
  };

  const onPointerDownNode = (nodeId: string, event: ReactPointerEvent) => {
    event.preventDefault();
    event.stopPropagation();
    const svg = svgRef.current;
    if (!svg) return;
    const pt = clientToSvg(svg, event.clientX, event.clientY, viewRef.current);
    const node = simNodesRef.current.find((n) => n.id === nodeId);
    if (!node) return;
    dragIdRef.current = nodeId;
    dragMovedRef.current = false;
    dragStartClientRef.current = { x: event.clientX, y: event.clientY };
    dragOffsetRef.current = { x: node.x - pt.x, y: node.y - pt.y };
    (event.currentTarget as Element).setPointerCapture?.(event.pointerId);
  };

  const onPointerMove = (event: ReactPointerEvent) => {
    if (panningRef.current) {
      const svg = svgRef.current;
      if (!svg) return;
      const rect = svg.getBoundingClientRect();
      const scaleX = sizeRef.current.width / rect.width;
      const scaleY = sizeRef.current.height / rect.height;
      const dx = (event.clientX - panStartRef.current.x) * scaleX;
      const dy = (event.clientY - panStartRef.current.y) * scaleY;
      setView({
        scale: viewRef.current.scale,
        tx: panStartRef.current.tx + dx,
        ty: panStartRef.current.ty + dy,
      });
      return;
    }

    if (!dragIdRef.current || !svgRef.current) return;
    const moved =
      Math.hypot(
        event.clientX - dragStartClientRef.current.x,
        event.clientY - dragStartClientRef.current.y
      ) > CLICK_MOVE_THRESHOLD;
    if (moved) {
      dragMovedRef.current = true;
    }
    const pt = clientToSvg(svgRef.current, event.clientX, event.clientY, viewRef.current);
    const node = simNodesRef.current.find((n) => n.id === dragIdRef.current);
    if (!node) return;
    node.x = pt.x + dragOffsetRef.current.x;
    node.y = pt.y + dragOffsetRef.current.y;
    node.vx = 0;
    node.vy = 0;
  };

  const onPointerUp = (event: ReactPointerEvent) => {
    if (panningRef.current) {
      panningRef.current = false;
      return;
    }
    const draggedId = dragIdRef.current;
    const wasClick = draggedId != null && !dragMovedRef.current;
    dragIdRef.current = null;
    if (wasClick && draggedId) {
      const selecting = selectedIdRef.current !== draggedId;
      setSelectedId((prev) => (prev === draggedId ? null : draggedId));
      // Center + mild zoom so focused edges are easier to read.
      const node = simNodesRef.current.find((n) => n.id === draggedId);
      if (node && selecting) {
        const targetScale = Math.max(viewRef.current.scale, 1.5);
        const { width, height } = sizeRef.current;
        setView({
          scale: Math.min(MAX_SCALE, targetScale),
          tx: width / 2 - node.x * Math.min(MAX_SCALE, targetScale),
          ty: height / 2 - node.y * Math.min(MAX_SCALE, targetScale),
        });
      }
    }
    void event;
  };

  const { width, height } = sizeRef.current;
  const simNodes = simNodesRef.current;
  const simEdges = simEdgesRef.current;
  const byId = new Map(simNodes.map((n) => [n.id, n]));
  void renderTick;

  if (nodes.length === 0) {
    return (
      <div className="flex h-full min-h-[420px] items-center justify-center rounded-[10px] border border-border bg-surface p-6 text-sm text-ink-muted">
        Brak osób do wyświetlenia na mapie relacji.
      </div>
    );
  }

  const selectedNode = selectedId ? byId.get(selectedId) : null;
  const focusEdges = selectedId
    ? simEdges.filter((e) => e.sourceId === selectedId || e.targetId === selectedId)
    : [];

  return (
    <div
      ref={containerRef}
      className="relative h-full min-h-[420px] w-full overflow-hidden rounded-[10px] border border-border bg-surface"
    >
      <div className="absolute right-3 top-3 z-10 flex items-center gap-1 rounded-[8px] border border-border bg-surface-raised/95 p-1 shadow-sm">
        <button
          type="button"
          className="btn-ghost h-8 w-8 p-0"
          aria-label="Przybliż"
          onClick={() => applyZoom(view.scale * 1.2)}
        >
          <Plus size={16} />
        </button>
        <button
          type="button"
          className="btn-ghost h-8 w-8 p-0"
          aria-label="Oddal"
          onClick={() => applyZoom(view.scale / 1.2)}
        >
          <Minus size={16} />
        </button>
        <button
          type="button"
          className="btn-ghost h-8 w-8 p-0"
          aria-label="Resetuj widok"
          onClick={() => {
            setView({ scale: 1, tx: 0, ty: 0 });
            setSelectedId(null);
          }}
        >
          <RotateCcw size={15} />
        </button>
        <span className="min-w-[3rem] px-1 text-center text-[11px] tabular-nums text-ink-muted">
          {Math.round(view.scale * 100)}%
        </span>
      </div>

      <svg
        ref={svgRef}
        width="100%"
        height="100%"
        viewBox={`0 0 ${width} ${height}`}
        className="absolute inset-0 h-full w-full touch-none"
        style={{ cursor: panningRef.current ? "grabbing" : "grab" }}
        onWheel={onWheel}
        onPointerDown={onBackgroundPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerLeave={onPointerUp}
        role="img"
        aria-label="Mapa relacji między osobami"
      >
        <defs>
          <filter id="node-shadow" x="-40%" y="-40%" width="180%" height="180%">
            <feDropShadow dx="0" dy="1" stdDeviation="2" floodOpacity="0.18" />
          </filter>
          <filter id="edge-glow" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="0" stdDeviation="2.5" floodColor="var(--accent)" floodOpacity="0.45" />
          </filter>
        </defs>

        <g transform={`translate(${view.tx} ${view.ty}) scale(${view.scale})`}>
          {simEdges.map((edge) => {
            const a = byId.get(edge.sourceId);
            const b = byId.get(edge.targetId);
            if (!a || !b) return null;

            const isFocusEdge =
              selectedId != null &&
              (edge.sourceId === selectedId || edge.targetId === selectedId);
            const dimmed = selectedId != null && !isFocusEdge;
            const isSpatial = edge.kind === "SPATIAL";
            const stroke = isFocusEdge
              ? "var(--accent)"
              : isSpatial
                ? "var(--accent)"
                : "var(--border-strong)";
            const strokeWidth = isFocusEdge
              ? Math.min(10, 4.5 + edge.weight * 0.7)
              : Math.min(5, 1.6 + edge.weight * 0.45);
            const opacity = dimmed ? 0.12 : isFocusEdge ? 0.95 : isSpatial ? 0.72 : 0.5;
            const midX = (a.x + b.x) / 2;
            const midY = (a.y + b.y) / 2;
            const label = `${edge.relation}${edge.weight > 1 ? ` · ${edge.weight}` : ""}`;
            const labelWidth = Math.min(
              isFocusEdge ? 220 : 150,
              Math.max(48, label.length * (isFocusEdge ? 7.2 : 6.2) + 14)
            );
            const labelHeight = isFocusEdge ? 24 : 18;

            return (
              <g
                key={`${edge.sourceId}-${edge.targetId}-${edge.kind}-${edge.relation}`}
                opacity={opacity}
              >
                <line
                  x1={a.x}
                  y1={a.y}
                  x2={b.x}
                  y2={b.y}
                  stroke={stroke}
                  strokeWidth={strokeWidth}
                  strokeLinecap="round"
                  strokeDasharray={isSpatial || isFocusEdge ? undefined : "7 5"}
                  filter={isFocusEdge ? "url(#edge-glow)" : undefined}
                />
                {(isFocusEdge || !selectedId) && (
                  <>
                    <rect
                      x={midX - labelWidth / 2}
                      y={midY - labelHeight / 2}
                      width={labelWidth}
                      height={labelHeight}
                      rx={7}
                      fill="var(--surface-raised)"
                      stroke={isFocusEdge ? "var(--accent)" : "var(--border)"}
                      strokeWidth={isFocusEdge ? 1.5 : 1}
                    />
                    <text
                      x={midX}
                      y={midY + (isFocusEdge ? 5 : 3.5)}
                      textAnchor="middle"
                      className={isFocusEdge ? "fill-ink" : "fill-ink-muted"}
                      style={{
                        fontSize: isFocusEdge ? 13 : 10,
                        fontWeight: isFocusEdge ? 600 : 400,
                      }}
                    >
                      {label}
                    </text>
                  </>
                )}
              </g>
            );
          })}

          {simNodes.map((node) => {
            const isSelected = selectedId === node.id;
            const isNeighbor = neighborIds.has(node.id);
            const dimmed = selectedId != null && !isNeighbor;
            const active =
              isSelected || hoveredId === node.id || dragIdRef.current === node.id;
            const radius = isSelected ? NODE_RADIUS + 6 : NODE_RADIUS;

            return (
              <g
                key={node.id}
                data-node={node.id}
                transform={`translate(${node.x}, ${node.y})`}
                style={{
                  cursor: "grab",
                  opacity: dimmed ? 0.28 : 1,
                }}
                onPointerDown={(e) => onPointerDownNode(node.id, e)}
                onPointerEnter={() => setHoveredId(node.id)}
                onPointerLeave={() => setHoveredId((id) => (id === node.id ? null : id))}
                onDoubleClick={(e) => {
                  e.stopPropagation();
                  router.push(`/knowledge/${node.id}`);
                }}
              >
                {isSelected && (
                  <circle
                    r={radius + 8}
                    fill="none"
                    stroke="var(--accent)"
                    strokeWidth={2}
                    strokeOpacity={0.35}
                  />
                )}
                <circle
                  r={radius}
                  fill={
                    isSelected || active
                      ? "var(--accent-muted)"
                      : "var(--surface-raised)"
                  }
                  stroke={
                    isSelected
                      ? "var(--accent)"
                      : active
                        ? "var(--accent)"
                        : "var(--border-strong)"
                  }
                  strokeWidth={isSelected ? 3 : active ? 2.5 : 1.5}
                  filter="url(#node-shadow)"
                />
                <text
                  textAnchor="middle"
                  y={5}
                  className="fill-ink"
                  style={{
                    fontSize: isSelected ? 13 : 11,
                    fontWeight: 600,
                    pointerEvents: "none",
                  }}
                >
                  {truncateLabel(node.displayName, isSelected ? 12 : 10)}
                </text>
                <title>
                  {node.displayName}
                  {` · ${node.photoCount} zdjęć · klik: focus relacji · przeciągnij · podwójne kliknięcie: album`}
                </title>
              </g>
            );
          })}
        </g>
      </svg>

      <div className="pointer-events-none absolute bottom-3 left-3 max-w-[min(100%-1.5rem,20rem)] rounded-[8px] border border-border bg-surface-raised/95 px-3 py-2 text-[11px] text-ink-muted shadow-sm">
        <div className="mb-1 font-medium text-ink">Legenda i sterowanie</div>
        <div className="flex items-center gap-2">
          <span className="inline-block h-0.5 w-5 bg-accent" />
          relacja przestrzenna / interakcja
        </div>
        <div className="mt-1 flex items-center gap-2">
          <span
            className="inline-block h-0 w-5 border-t border-dashed border-border-strong"
            aria-hidden
          />
          współwystępowanie na zdjęciu
        </div>
        <div className="mt-1.5 space-y-0.5">
          <div>Kółko myszy / +− — przybliż</div>
          <div>Klik w osobę — powiększ linie do sąsiadów</div>
          <div>Przeciągnij tło — przesuń mapę · 2× klik — album</div>
        </div>
        {selectedNode && (
          <div className="mt-1.5 border-t border-border pt-1.5 text-ink">
            Focus: <span className="font-medium">{selectedNode.displayName}</span>
            {focusEdges.length > 0
              ? ` · ${focusEdges.length} ${focusEdges.length === 1 ? "relacja" : focusEdges.length < 5 ? "relacje" : "relacji"}`
              : " · brak relacji"}
          </div>
        )}
      </div>
    </div>
  );
}

function isNeighborEdge(
  focusId: string,
  aId: string,
  bId: string,
  edges: SimEdge[]
): boolean {
  return edges.some(
    (e) =>
      (e.sourceId === focusId && (e.targetId === aId || e.targetId === bId)) ||
      (e.targetId === focusId && (e.sourceId === aId || e.sourceId === bId))
  );
}

function clientToSvg(
  svg: SVGSVGElement,
  clientX: number,
  clientY: number,
  view: ViewTransform
) {
  const rect = svg.getBoundingClientRect();
  const vbWidth = svg.viewBox.baseVal.width || rect.width;
  const vbHeight = svg.viewBox.baseVal.height || rect.height;
  const localX = ((clientX - rect.left) / rect.width) * vbWidth;
  const localY = ((clientY - rect.top) / rect.height) * vbHeight;
  return {
    x: (localX - view.tx) / view.scale,
    y: (localY - view.ty) / view.scale,
  };
}

function truncateLabel(label: string, max: number) {
  if (label.length <= max) return label;
  return `${label.slice(0, max - 1)}…`;
}
