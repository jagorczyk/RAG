"use client";

import { IdentityReviewPanel } from "@/components/knowledge/IdentityReviewPanel";
import { EntitiesPanel } from "@/components/knowledge/EntitiesPanel";
import { Network } from "lucide-react";
import { useRouter } from "next/navigation";
import { IconButton } from "@/components/ui/IconButton";

export default function KnowledgePage() {
  const router = useRouter();

  return (
    <div className="page-shell">
      <header className="flex items-start justify-between gap-3 px-5 pt-4 pb-2">
        <div>
          <h1 className="page-title">Osoby</h1>
          <p className="page-subtitle">Rozpoznane twarze w Twojej bibliotece</p>
        </div>
        <IconButton
          label="Mapa relacji"
          onClick={() => router.push("/knowledge/graph")}
        >
          <Network size={18} />
        </IconButton>
      </header>
      <div className="page-body mx-auto max-w-5xl space-y-8 !pt-3">
        <EntitiesPanel />
        <IdentityReviewPanel />
      </div>
    </div>
  );
}
