"use client";

import { User, Bot, FileText, Image as ImageIcon, ExternalLink } from "lucide-react";
import { Message, Source, Folder, FileItem } from "@/lib/api";
import { useRouter } from "next/navigation";

interface ChatMessageBubbleProps {
  msg: Message;
  allFolders: Folder[];
  allFiles: FileItem[];
  onImageClick: (url: string) => void;
}

export function ChatMessageBubble({ msg, allFolders, allFiles, onImageClick }: ChatMessageBubbleProps) {
  const router = useRouter();

  const renderSourceIcon = (type: Source["type"]) => {
    switch (type) {
      case "IMAGE": return <ImageIcon size={14} />;
      case "PDF":
      case "TEXT": return <FileText size={14} />;
      default: return <ExternalLink size={14} />;
    }
  };

  const getSourceImageUrl = (source: Source) => {
    if (source.type === "IMAGE" && source.base64) {
      const mime = source.fileName.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
      return `data:${mime};base64,${source.base64}`;
    }
    return null;
  };

  return (
    <div className={`flex flex-col ${msg.role === "user" ? "items-end" : "items-start"}`}>
      <div className={`flex gap-4 max-w-[85%] flex-row`}>
        {msg.role === "assistant" && (
          <div className="w-8 h-8 rounded-full bg-slate-900 flex items-center justify-center shrink-0 mt-1">
            <Bot size={18} className="text-white" />
          </div>
        )}
        
        <div className={`flex flex-col gap-2 ${msg.role === "user" ? "items-end" : "items-start"}`}>
          <div className={`rounded-2xl p-4 ${
            msg.role === "user" 
              ? "bg-gray-100 text-gray-900 rounded-tr-sm" 
              : "bg-white border border-gray-200 text-gray-800 rounded-tl-sm shadow-sm"
          }`}>
            <div className="whitespace-pre-wrap leading-relaxed text-sm md:text-base">
              {msg.content.split(/(@[\w\-\.\/\u00C0-\u017F]+)/g).map((part, i) => {
                if (part.startsWith("@")) {
                  let mentionName = part.slice(1);
                  let trailingPunctuation = "";
                  
                  const match = mentionName.match(/[:,\.\?\!]+$/);
                  if (match) {
                    trailingPunctuation = match[0];
                    mentionName = mentionName.slice(0, -trailingPunctuation.length);
                  }

                  const folderMatch = allFolders.find(f => f.name === mentionName);
                  const fileMatch = allFiles.find(f => f.name === mentionName);
                  
                  if (folderMatch || fileMatch) {
                    return (
                      <span key={i}>
                        <button
                          onClick={() => {
                            if (folderMatch) {
                              router.push(`/folders/${folderMatch.id}`);
                            } else if (fileMatch) {
                              const folderName = fileMatch.id.split("/")[2];
                              const folder = allFolders.find(f => f.name === folderName);
                              if (folder) router.push(`/folders/${folder.id}`);
                            }
                          }}
                          className="font-semibold text-blue-800 bg-blue-50 border border-blue-100 px-1.5 py-0.5 rounded-md mx-0.5 inline-block hover:bg-blue-100 hover:border-blue-200 transition-colors cursor-pointer"
                        >
                          @{mentionName}
                        </button>
                        {trailingPunctuation}
                      </span>
                    );
                  }
                }
                
                return (
                  <span key={i}>
                    {part.split(/(\*\*.*?\*\*)/g).map((subPart, subIdx) => {
                      if (subPart.startsWith("**") && subPart.endsWith("**")) {
                        return <strong key={subIdx} className="font-bold text-slate-900">{subPart.slice(2, -2)}</strong>;
                      }
                      return subPart;
                    })}
                  </span>
                );
              })}
            </div>
          </div>

          {msg.sources && msg.sources.length > 0 && (
            <div className="flex flex-wrap gap-2 mt-1">
              {msg.sources.map((source, sIdx) => {
                const imageUrl = getSourceImageUrl(source);
                return (
                  <div 
                    key={sIdx}
                    onClick={() => imageUrl && onImageClick(imageUrl)}
                    className={`flex items-center gap-1.5 px-2 py-1 bg-gray-50 border border-gray-200 rounded text-[10px] text-gray-500 font-medium hover:bg-gray-100 transition-colors ${imageUrl ? "cursor-zoom-in" : "cursor-help"}`}
                    title={`${source.fileName} (score: ${source.score.toFixed(4)})`}
                  >
                    {imageUrl ? (
                      <div className="w-4 h-4 rounded-sm overflow-hidden bg-gray-200">
                        <img src={imageUrl} alt="" className="w-full h-full object-cover" />
                      </div>
                    ) : renderSourceIcon(source.type)}
                    <span className="max-w-[120px] truncate">{source.fileName}</span>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {msg.role === "user" && (
          <div className="w-8 h-8 rounded-full bg-gray-300 flex items-center justify-center shrink-0 mt-1">
            <User size={18} className="text-gray-600" />
          </div>
        )}
      </div>
    </div>
  );
}
