"use client";

import { IdentityReviewPanel } from "@/components/knowledge/IdentityReviewPanel";
import { EntitiesPanel } from "@/components/knowledge/EntitiesPanel";
import { Network } from "lucide-react";
import { useRouter } from "next/navigation";
import { IconButton } from "@/components/ui/IconButton";
import { PageHeader } from "@/components/ui/PageHeader";

export default function KnowledgePage() {
  const router = useRouter();

  return (
    <div className="page-shell">
      <PageHeader
        title="Osoby"
        subtitle="Rozpoznane twarze w Twojej bibliotece"
        action={
          <IconButton
            label="Mapa relacji"
            onClick={() => router.push("/knowledge/graph")}
          >
            <Network size={18} />
          </IconButton>
        }
      />
      <div className="page-body mx-auto max-w-5xl space-y-8 !pt-4">
        <EntitiesPanel />
        <IdentityReviewPanel />
      </div>
    </div>
  );
}
