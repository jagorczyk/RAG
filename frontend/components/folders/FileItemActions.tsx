"use client";

import { Edit2, FileText, Layers, Maximize2 } from "lucide-react";
import type { FileItem } from "@/lib/api";

interface FileItemActionsProps {
  file: FileItem;
  onRename: (file: FileItem) => void;
  onEmbeddings: (file: FileItem) => void;
  onPreview: (file: FileItem) => void;
  compact?: boolean;
}

export function FileItemActions({
  file,
  onRename,
  onEmbeddings,
  onPreview,
  compact = false,
}: FileItemActionsProps) {
  const buttonClass = compact ? "btn-ghost p-1" : "btn-ghost p-1.5";

  return (
    <>
      <button
        type="button"
        onClick={() => onRename(file)}
        className={buttonClass}
        title="Zmień nazwę"
      >
        <Edit2 size={compact ? 14 : 16} />
      </button>
      <button
        type="button"
        onClick={() => onEmbeddings(file)}
        className={buttonClass}
        title="Embeddingi"
      >
        <Layers size={compact ? 14 : 16} />
      </button>
      {file.url && file.type.includes("image") && (
        <button
          type="button"
          onClick={() => onPreview(file)}
          className={buttonClass}
          title="Powiększ"
        >
          <Maximize2 size={compact ? 14 : 16} />
        </button>
      )}
      {(file.type.includes("pdf") || file.type.includes("text")) && (
        <button
          type="button"
          onClick={() => onPreview(file)}
          className={buttonClass}
          title="Podgląd treści"
        >
          <FileText size={compact ? 14 : 16} />
        </button>
      )}
    </>
  );
}
