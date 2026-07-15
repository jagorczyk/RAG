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
      <header className="page-header">
        <div className="mx-auto max-w-6xl">
          <button onClick={() => router.push("/knowledge")} className="btn-ghost -ml-2 mb-2 px-2">
            <ArrowLeft size={16} /> Powrót do osób
          </button>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-[10px] bg-accent-muted text-accent">
                <Network size={20} />
              </span>
              <div>
                <h1 className="page-title">Mapa relacji</h1>
                <p className="mt-0.5 text-sm text-ink-muted">
                  Relacje między osobami — przeciągaj węzły, podwójne kliknięcie otwiera album
                </p>
              </div>
            </div>
            {graph && !isLoading && (
              <p className="text-xs text-ink-muted">
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
          </div>
        </div>
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
