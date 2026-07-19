"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Network } from "lucide-react";
import { getPersonRelationGraph, PersonRelationGraph as GraphData } from "@/lib/knowledge-api";
import { PersonRelationGraph } from "@/components/knowledge/PersonRelationGraph";
import { PageHeader } from "@/components/ui/PageHeader";
import { EmptyState } from "@/components/ui/EmptyState";

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
      <PageHeader
        title="Mapa relacji"
        subtitle="Klik w osobę powiększa linie · zoom kółkiem · 2× klik otwiera album"
        onBack={() => router.push("/knowledge")}
        action={
          graph && !isLoading ? (
            <p className="text-xs font-semibold text-ink-muted">
              {graph.nodes.length}{" "}
              {graph.nodes.length === 1
                ? "osoba"
                : graph.nodes.length < 5
                  ? "osoby"
                  : "osób"}
              {" · "}
              {graph.edges.length}{" "}
              {graph.edges.length === 1
                ? "relacja"
                : graph.edges.length < 5
                  ? "relacje"
                  : "relacji"}
            </p>
          ) : (
            <Network size={18} className="text-ink-muted" aria-hidden />
          )
        }
      />

      <div className="page-body mx-auto flex max-w-6xl flex-1 flex-col !pt-4">
        {isLoading ? (
          <div
            className="flex flex-1 items-center justify-center gap-2 py-16 text-ink-muted"
            aria-busy="true"
          >
            <Loader2 className="animate-spin" size={20} aria-hidden />
            Wczytywanie mapy…
          </div>
        ) : error ? (
          <EmptyState
            icon="☁️"
            title="Nie udało się wczytać mapy"
            description={error}
            action={
              <button type="button" className="btn-secondary" onClick={() => void loadGraph()}>
                Spróbuj ponownie
              </button>
            }
          />
        ) : graph && graph.nodes.length === 0 ? (
          <EmptyState
            icon="👤"
            title="Brak nazwanych osób"
            description="Dodaj i potwierdź tożsamości na zdjęciach, aby zobaczyć mapę relacji."
            action={
              <button
                type="button"
                className="btn-primary"
                onClick={() => router.push("/knowledge")}
              >
                Przejdź do osób
              </button>
            }
          />
        ) : graph && graph.edges.length === 0 ? (
          <div className="flex min-h-[480px] flex-1 flex-col gap-3">
            <div className="status-banner status-banner-info !mb-0" role="status">
              Osoby są widoczne, ale brak potwierdzonych relacji między nimi (wspólne zdjęcia
              lub relacje przestrzenne).
            </div>
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
