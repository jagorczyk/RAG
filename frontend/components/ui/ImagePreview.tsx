"use client";

import { X } from "lucide-react";

interface ImagePreviewProps {
  url: string;
  onClose: () => void;
}

export function ImagePreview({ url, onClose }: ImagePreviewProps) {
  return (
    <div 
      className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center p-4 animate-in fade-in duration-200"
      onClick={onClose}
    >
      <button 
        className="absolute top-6 right-6 p-2 text-white/70 hover:text-white hover:bg-white/10 rounded-full transition-colors"
        onClick={onClose}
      >
        <X size={32} />
      </button>
      <img 
        src={url} 
        alt="Preview" 
        className="max-w-full max-h-full object-contain rounded-lg shadow-2xl animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      />
    </div>
  );
}
