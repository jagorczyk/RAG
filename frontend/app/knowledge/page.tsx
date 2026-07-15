"use client";

import { IdentityReviewPanel } from "@/components/knowledge/IdentityReviewPanel";
import { EntitiesPanel } from "@/components/knowledge/EntitiesPanel";
import { ArrowLeft, Network, Users } from "lucide-react";
import { useRouter } from "next/navigation";

export default function KnowledgePage() {
  const router = useRouter();

  return (
    <div className="page-shell">
      <header className="page-header">
        <div className="mx-auto max-w-6xl">
          <button onClick={() => router.push("/")} className="btn-ghost -ml-2 mb-2 px-2">
            <ArrowLeft size={16} /> Powrót
          </button>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-[10px] bg-accent-muted text-accent">
                <Users size={20} />
              </span>
              <h1 className="page-title">Osoby</h1>
            </div>
            <button
              type="button"
              onClick={() => router.push("/knowledge/graph")}
              className="btn-secondary"
            >
              <Network size={16} />
              Mapa relacji
            </button>
          </div>
        </div>
      </header>
      <div className="page-body mx-auto max-w-6xl space-y-6">
        <EntitiesPanel />
        <IdentityReviewPanel />
      </div>
    </div>
  );
}
