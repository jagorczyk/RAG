"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Loader2, Network } from "lucide-react";
import { getPersonRelationGraph, PersonRelationGraph as GraphData } from "@/lib/knowledge-api";
import { PersonRelationGraph } from "@/components/knowledge/PersonRelationGraph";

export default function PersonGraphPage() {
  const router = useRouter();
  const [graph, setGraph] = useState<GraphData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadGraph = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getPersonRelationGraph();
      setGraph(data);
    } catch (e) {
      console.error(e);
      setError("Nie udało się wczytać mapy relacji.");
      setGraph(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // Data loading is the external synchronization this effect is responsible for.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadGraph();
  }, [loadGraph]);

  return (
    <div className="page-shell">
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-border px-5 pt-4 pb-4">
        <div className="flex min-w-0 items-start gap-2">
          <button
            type="button"
            onClick={() => router.push("/knowledge")}
            className="icon-button -ml-1 mt-0.5 shadow-none"
            aria-label="Wróć do osób"
          >
            <ArrowLeft size={20} />
          </button>
          <div>
            <h1 className="page-title flex items-center gap-2">
              <Network size={22} className="shrink-0" />
              Mapa relacji
            </h1>
            <p className="page-subtitle">
              Klik w osobę powiększa linie · zoom kółkiem · 2× klik otwiera album
            </p>
          </div>
        </div>
        {graph && !isLoading && (
          <p className="text-xs font-semibold text-ink-muted">
            {graph.nodes.length}{" "}
            {graph.nodes.length === 1 ? "osoba" : graph.nodes.length < 5 ? "osoby" : "osób"}
            {" · "}
            {graph.edges.length}{" "}
            {graph.edges.length === 1
              ? "relacja"
              : graph.edges.length < 5
                ? "relacje"
                : "relacji"}
          </p>
        )}
      </header>

      <div className="page-body mx-auto flex max-w-6xl flex-1 flex-col">
        {isLoading ? (
          <div className="flex flex-1 items-center justify-center gap-2 py-16 text-ink-muted">
            <Loader2 className="animate-spin" size={20} />
            Wczytywanie mapy…
          </div>
        ) : error ? (
          <div className="rounded-[10px] border border-border bg-surface p-6 text-center">
            <p className="text-sm text-ink-muted">{error}</p>
            <button type="button" className="btn-secondary mt-4" onClick={() => void loadGraph()}>
              Spróbuj ponownie
            </button>
          </div>
        ) : graph && graph.nodes.length === 0 ? (
          <div className="rounded-[10px] border border-border bg-surface p-6 text-center text-sm text-ink-muted">
            Brak nazwanych osób w bazie. Dodaj i potwierdź tożsamości na zdjęciach, aby zobaczyć mapę.
          </div>
        ) : graph && graph.edges.length === 0 ? (
          <div className="flex min-h-[480px] flex-1 flex-col gap-3">
            <p className="text-sm text-ink-muted">
              Osoby są widoczne, ale brak potwierdzonych relacji między nimi (wspólne zdjęcia lub
              relacje przestrzenne).
            </p>
            <div className="min-h-0 flex-1">
              <PersonRelationGraph nodes={graph.nodes} edges={graph.edges} />
            </div>
          </div>
        ) : (
          graph && (
            <div className="min-h-[480px] flex-1">
              <PersonRelationGraph nodes={graph.nodes} edges={graph.edges} />
            </div>
          )
        )}
      </div>
    </div>
  );
}
