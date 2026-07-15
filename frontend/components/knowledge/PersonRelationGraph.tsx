"use client";

import { useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { useRouter } from "next/navigation";
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

const NODE_RADIUS = 28;
const REPULSION = 4200;
const SPRING = 0.02;
const SPRING_LENGTH = 160;
const CENTER_PULL = 0.008;
const DAMPING = 0.86;
const MAX_SPEED = 8;

export function PersonRelationGraph({ nodes, edges }: PersonRelationGraphProps) {
  const router = useRouter();
  const containerRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const simNodesRef = useRef<SimNode[]>([]);
  const simEdgesRef = useRef<SimEdge[]>([]);
  const dragIdRef = useRef<string | null>(null);
  const dragOffsetRef = useRef({ x: 0, y: 0 });
  const sizeRef = useRef({ width: 800, height: 560 });
  const frameRef = useRef<number | null>(null);
  const [renderTick, setRenderTick] = useState(0);
  const [hoveredId, setHoveredId] = useState<string | null>(null);

  const nodeIds = useMemo(() => nodes.map((n) => n.id).join(","), [nodes]);
  const edgeKey = useMemo(
    () => edges.map((e) => `${e.sourceId}-${e.targetId}-${e.kind}-${e.relation}`).join("|"),
    [edges]
  );

  useEffect(() => {
    const width = sizeRef.current.width;
    const height = sizeRef.current.height;
    const cx = width / 2;
    const cy = height / 2;
    const radius = Math.min(width, height) * 0.28;

    simNodesRef.current = nodes.map((node, index) => {
      const angle = (index / Math.max(nodes.length, 1)) * Math.PI * 2;
      return {
        id: node.id,
        displayName: node.displayName,
        photoCount: node.photoCount,
        x: cx + Math.cos(angle) * radius + (Math.random() - 0.5) * 20,
        y: cy + Math.sin(angle) * radius + (Math.random() - 0.5) * 20,
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
          const force = REPULSION / distSq;
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
        const targetLen = SPRING_LENGTH + Math.min(edge.weight, 8) * 8;
        const force = (dist - targetLen) * SPRING;
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
        node.vx += (cx - node.x) * CENTER_PULL;
        node.vy += (cy - node.y) * CENTER_PULL;
        node.vx *= DAMPING;
        node.vy *= DAMPING;
        const speed = Math.sqrt(node.vx * node.vx + node.vy * node.vy);
        if (speed > MAX_SPEED) {
          node.vx = (node.vx / speed) * MAX_SPEED;
          node.vy = (node.vy / speed) * MAX_SPEED;
        }
        node.x += node.vx;
        node.y += node.vy;
        node.x = Math.min(width - NODE_RADIUS, Math.max(NODE_RADIUS, node.x));
        node.y = Math.min(height - NODE_RADIUS, Math.max(NODE_RADIUS, node.y));
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

  const onPointerDown = (nodeId: string, event: ReactPointerEvent) => {
    event.preventDefault();
    const svg = svgRef.current;
    if (!svg) return;
    const pt = clientToSvg(svg, event.clientX, event.clientY);
    const node = simNodesRef.current.find((n) => n.id === nodeId);
    if (!node) return;
    dragIdRef.current = nodeId;
    dragOffsetRef.current = { x: node.x - pt.x, y: node.y - pt.y };
    (event.target as Element).setPointerCapture?.(event.pointerId);
  };

  const onPointerMove = (event: ReactPointerEvent) => {
    if (!dragIdRef.current || !svgRef.current) return;
    const pt = clientToSvg(svgRef.current, event.clientX, event.clientY);
    const node = simNodesRef.current.find((n) => n.id === dragIdRef.current);
    if (!node) return;
    node.x = pt.x + dragOffsetRef.current.x;
    node.y = pt.y + dragOffsetRef.current.y;
    node.vx = 0;
    node.vy = 0;
  };

  const onPointerUp = () => {
    dragIdRef.current = null;
  };

  const { width, height } = sizeRef.current;
  const simNodes = simNodesRef.current;
  const simEdges = simEdgesRef.current;
  const byId = new Map(simNodes.map((n) => [n.id, n]));
  // renderTick forces re-render each animation frame
  void renderTick;

  if (nodes.length === 0) {
    return (
      <div className="flex h-full min-h-[420px] items-center justify-center rounded-[10px] border border-border bg-surface p-6 text-sm text-ink-muted">
        Brak osób do wyświetlenia na mapie relacji.
      </div>
    );
  }

  return (
    <div ref={containerRef} className="relative h-full min-h-[420px] w-full overflow-hidden rounded-[10px] border border-border bg-surface">
      <svg
        ref={svgRef}
        width="100%"
        height="100%"
        viewBox={`0 0 ${width} ${height}`}
        className="absolute inset-0 h-full w-full touch-none"
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
        </defs>

        {simEdges.map((edge) => {
          const a = byId.get(edge.sourceId);
          const b = byId.get(edge.targetId);
          if (!a || !b) return null;
          const midX = (a.x + b.x) / 2;
          const midY = (a.y + b.y) / 2;
          const isSpatial = edge.kind === "SPATIAL";
          const stroke = isSpatial ? "var(--accent)" : "var(--border-strong)";
          const strokeWidth = Math.min(4, 1 + edge.weight * 0.35);
          return (
            <g key={`${edge.sourceId}-${edge.targetId}-${edge.kind}-${edge.relation}`}>
              <line
                x1={a.x}
                y1={a.y}
                x2={b.x}
                y2={b.y}
                stroke={stroke}
                strokeWidth={strokeWidth}
                strokeOpacity={isSpatial ? 0.75 : 0.45}
                strokeDasharray={isSpatial ? undefined : "6 4"}
              />
              <rect
                x={midX - Math.min(70, edge.relation.length * 3.5) - 6}
                y={midY - 10}
                width={Math.min(140, edge.relation.length * 7 + 12)}
                height={18}
                rx={6}
                fill="var(--surface-raised)"
                stroke="var(--border)"
                opacity={0.92}
              />
              <text
                x={midX}
                y={midY + 3}
                textAnchor="middle"
                className="fill-ink-muted"
                style={{ fontSize: 10 }}
              >
                {edge.relation}
                {edge.weight > 1 ? ` · ${edge.weight}` : ""}
              </text>
            </g>
          );
        })}

        {simNodes.map((node) => {
          const active = hoveredId === node.id || dragIdRef.current === node.id;
          return (
            <g
              key={node.id}
              transform={`translate(${node.x}, ${node.y})`}
              style={{ cursor: "grab" }}
              onPointerDown={(e) => onPointerDown(node.id, e)}
              onPointerEnter={() => setHoveredId(node.id)}
              onPointerLeave={() => setHoveredId((id) => (id === node.id ? null : id))}
              onDoubleClick={() => router.push(`/knowledge/${node.id}`)}
            >
              <circle
                r={NODE_RADIUS}
                fill={active ? "var(--accent-muted)" : "var(--surface-raised)"}
                stroke={active ? "var(--accent)" : "var(--border-strong)"}
                strokeWidth={active ? 2.5 : 1.5}
                filter="url(#node-shadow)"
              />
              <text
                textAnchor="middle"
                y={4}
                className="fill-ink"
                style={{ fontSize: 11, fontWeight: 600, pointerEvents: "none" }}
              >
                {truncateLabel(node.displayName, 10)}
              </text>
              <title>
                {node.displayName}
                {` · ${node.photoCount} zdjęć · przeciągnij, podwójne kliknięcie otwiera album`}
              </title>
            </g>
          );
        })}
      </svg>

      <div className="pointer-events-none absolute bottom-3 left-3 rounded-[8px] border border-border bg-surface-raised/95 px-3 py-2 text-[11px] text-ink-muted shadow-sm">
        <div className="mb-1 font-medium text-ink">Legenda</div>
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
        <div className="mt-1">Przeciągnij węzeł · podwójne kliknięcie → album</div>
      </div>
    </div>
  );
}

function clientToSvg(svg: SVGSVGElement, clientX: number, clientY: number) {
  const pt = svg.createSVGPoint();
  pt.x = clientX;
  pt.y = clientY;
  const ctm = svg.getScreenCTM();
  if (!ctm) {
    return { x: clientX, y: clientY };
  }
  const local = pt.matrixTransform(ctm.inverse());
  return { x: local.x, y: local.y };
}

function truncateLabel(label: string, max: number) {
  if (label.length <= max) return label;
  return `${label.slice(0, max - 1)}…`;
}
