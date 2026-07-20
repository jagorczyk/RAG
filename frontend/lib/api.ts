// When both applications run locally, call Spring directly. This avoids a
// stale reverse-proxy socket after the backend is restarted. Deployed and
// mobile clients retain the configured backend URL or same-origin proxy.
import { apiFetch, getToken } from "./auth";

const BASE_URL = process.env.NEXT_PUBLIC_BACKEND_URL
  ?? (typeof window !== "undefined" && window.location.hostname === "localhost"
    ? "http://localhost:8080"
    : "");

export interface ReanalysisStatus {
  jobId: string;
  status: "RUNNING" | "COMPLETED";
  total: number;
  completed: number;
  failed: number;
  remaining: number;
}

export async function startContextReanalysis(): Promise<ReanalysisStatus> {
  const response = await apiFetch(`${BASE_URL}/api/data/images/reanalyze-context`, { method: "POST" });
  if (!response.ok) throw new Error("Failed to start context reanalysis");
  return response.json();
}

export async function getContextReanalysisStatus(jobId: string): Promise<ReanalysisStatus> {
  const response = await apiFetch(`${BASE_URL}/api/data/images/reanalyze-context/${jobId}`);
  if (!response.ok) throw new Error("Failed to read context reanalysis status");
  return response.json();
}

export interface Chat {
  id: string;
  title: string;
  updatedAt: string;
}

export interface Folder {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface FileItem {
  id: string;
  name: string;
  type: string;
  url: string;
  extractedText?: string;
}

export interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  sources?: Source[];
  uncertain?: boolean;
  evidence?: QueryEvidence[];
  answerKind?: "GRAPH" | "GRAPH_QUERY" | "DOCUMENT" | "HYBRID" | "VISUAL_VALIDATION" | "NO_EVIDENCE";
}

export interface QueryEvidence {
  path: string;
  confidence: number;
  reasons: string[];
  matchStatus?: "MATCH" | "CONFIRMED" | "RETRIEVED";
  retrievalScore?: number;
  entityConfidence?: number;
  conditionConfidence?: number;
}

export interface Source {
  path: string;
  fileName: string;
  score: number;
  base64?: string;
  type: "PDF" | "TEXT" | "IMAGE" | "OTHER" | "GRAPH_FACT";
}

export interface FilePreview {
  kind: "image" | "pdf" | "text" | "other";
  title: string;
  mimeType: string;
  content: string;
  path?: string;
}

interface ApiFolder {
  id: string;
  name: string;
  createdAt?: string;
  updatedAt?: string;
}

function mapFolder(folder: ApiFolder): Folder {
  return {
    id: folder.id,
    name: folder.name,
    createdAt: folder.createdAt ?? "",
    updatedAt: folder.updatedAt ?? folder.createdAt ?? "",
  };
}

interface ApiFile {
  path: string;
  name: string;
  fileType: string;
  imageBase64?: string;
  extractedText?: string;
}

interface ApiMessage {
  type: "USER" | "ASSISTANT" | "AI";
  text: string;
  sources?: Source[];
  evidence?: QueryEvidence[];
  uncertain?: boolean;
  answerKind?: Message["answerKind"];
}

export async function getChats(): Promise<Chat[]> {
  const response = await apiFetch(`${BASE_URL}/api/chat/all`);
  if (!response.ok) throw new Error("Failed to fetch chats");
  const chatIds: string[] = await response.json();
  
  return chatIds.map(id => ({
    id,
    title: `Chat ${id.slice(0, 8)}`,
    updatedAt: new Date().toISOString()
  })); 
}

export async function createChat(): Promise<Chat> {
  const response = await apiFetch(`${BASE_URL}/api/chat/create`, {
    method: "POST"
  });
  if (!response.ok) throw new Error("Failed to create chat");
  const data = await response.json();
  const id = data.id;
  
  return {
    id,
    title: `Nowa konwersacja`,
    updatedAt: new Date().toISOString()
  };
}

export async function getFolders(): Promise<Folder[]> {
  const response = await apiFetch(`${BASE_URL}/api/folders`);
  if (!response.ok) throw new Error("Failed to fetch folders");
  const folders: ApiFolder[] = await response.json();
  
  return folders.map(mapFolder);
}

export async function createFolder(name: string): Promise<Folder> {
  const response = await apiFetch(`${BASE_URL}/api/folders/create`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ name })
  });
  if (!response.ok) throw new Error("Failed to create folder");
  const folder: ApiFolder = await response.json();
  
  return mapFolder(folder);
}

export async function deleteFolder(id: string): Promise<void> {
  const response = await apiFetch(`${BASE_URL}/api/folders/${id}`, {
    method: "DELETE"
  });
  if (!response.ok) throw new Error("Failed to delete folder");
}

export async function getAllFiles(): Promise<FileItem[]> {
  const response = await apiFetch(`${BASE_URL}/api/data/files`);
  if (!response.ok) throw new Error("Failed to fetch files");
  const allFiles: ApiFile[] = await response.json();
  
  return allFiles.map(f => ({
    id: f.path,
    name: f.name,
    type: f.fileType,
    url: f.imageBase64 ? `data:${f.fileType};base64,${f.imageBase64}` : "",
    extractedText: f.extractedText || undefined,
  }));
}

export async function getFilesInFolder(folderName: string): Promise<FileItem[]> {
  const response = await apiFetch(`${BASE_URL}/api/data/files`);
  if (!response.ok) throw new Error("Failed to fetch files");
  const allFiles: ApiFile[] = await response.json();
  
  const folderPrefix = `dir://${folderName}/`;
  return allFiles
    .filter(f => f.path.startsWith(folderPrefix))
    .map(f => ({
      id: f.path,
      name: f.name,
      type: f.fileType,
      url: f.imageBase64 ? `data:${f.fileType};base64,${f.imageBase64}` : "",
      extractedText: f.extractedText || undefined,
    }));
}

export async function uploadFileToFolder(folderId: string, file: File, entityTag?: string): Promise<void> {
  await uploadFileToFolderWithProgress(folderId, file, 0, 1, undefined, entityTag);
}

export type UploadProgress = {
  percent: number;
  fileIndex: number;
  fileTotal: number;
  fileName: string;
  phase: "uploading" | "processing";
};

export interface UploadResult {
  path: string;
  fileName: string;
  image: boolean;
  status?: "PENDING" | "READY" | "FAILED" | "EXTRACTED" | "NEEDS_REVIEW" | string;
}

export async function getIngestionStatus(path: string): Promise<{ path: string; status: string }> {
  const params = new URLSearchParams({ path });
  const response = await apiFetch(`${BASE_URL}/api/data/files/ingestion-status?${params.toString()}`);
  if (!response.ok) throw new Error("Failed to read ingestion status");
  return response.json();
}

/** Poll until READY/FAILED (or timeout). Used after async upload (202 + PENDING). */
export async function waitForIngestionReady(
  path: string,
  options?: { timeoutMs?: number; intervalMs?: number; onTick?: (status: string) => void }
): Promise<string> {
  const timeoutMs = options?.timeoutMs ?? 10 * 60 * 1000;
  const intervalMs = options?.intervalMs ?? 1500;
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const { status } = await getIngestionStatus(path);
    options?.onTick?.(status);
    if (status === "READY" || status === "NEEDS_REVIEW") return status;
    if (status === "FAILED") throw new Error("Przetwarzanie pliku nie powiodło się.");
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  throw new Error("Przekroczono limit czasu oczekiwania na przetworzenie pliku.");
}

/** Per-file progress (0..1). Used by concurrent multi-upload aggregation. */
export type FileUploadProgressUpdate = {
  ratio: number;
  phase: UploadProgress["phase"];
  fileName: string;
};

/**
 * Upload one file; reports per-file ratio via onFileProgress.
 * Prefer this for concurrent pools — overall percent is aggregated by the caller.
 */
export function uploadFileToFolderTracked(
  folderId: string,
  file: File,
  entityTag?: string,
  onFileProgress?: (update: FileUploadProgressUpdate) => void
): Promise<UploadResult> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const formData = new FormData();
    formData.append("file", file);

    const report = (fileRatio: number, phase: UploadProgress["phase"]) => {
      const clampedRatio = Math.max(0, Math.min(1, fileRatio));
      onFileProgress?.({
        ratio: clampedRatio,
        phase,
        fileName: file.name,
      });
    };

    xhr.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable && event.total > 0) {
        report(event.loaded / event.total, "uploading");
      }
    });

    xhr.upload.addEventListener("loadend", () => {
      report(0.92, "processing");
    });

    xhr.addEventListener("load", () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        report(0.95, "processing");
        let parsed: UploadResult;
        try {
          parsed = JSON.parse(xhr.responseText) as UploadResult;
        } catch {
          parsed = {
            path: "",
            fileName: file.name,
            image: file.type.startsWith("image/"),
          };
        }

        // Async ingest: HTTP returned quickly with PENDING — poll worker status.
        if (parsed.path && parsed.status === "PENDING") {
          waitForIngestionReady(parsed.path, {
            onTick: () => report(0.95, "processing"),
          })
            .then((finalStatus) => {
              report(1, "processing");
              resolve({ ...parsed, status: finalStatus });
            })
            .catch(reject);
          return;
        }

        report(1, "processing");
        resolve(parsed);
        return;
      }
      let message = "Nie udało się wgrać pliku.";
      try {
        const response = JSON.parse(xhr.responseText) as { message?: string; detail?: string };
        message = response.message ?? response.detail ?? message;
      } catch {
        // Keep the fallback for non-JSON responses and connection proxies.
      }
      reject(new Error(message));
    });

    xhr.addEventListener("error", () => {
      reject(new Error("Failed to upload file"));
    });

    let url = `${BASE_URL}/api/folders/${folderId}/upload`;
    if (entityTag) {
      url += `?entityTag=${encodeURIComponent(entityTag)}`;
    }
    xhr.open("POST", url);
    const token = getToken();
    if (token) {
      xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    }
    xhr.send(formData);

    report(0, "uploading");
  });
}

export function uploadFileToFolderWithProgress(
  folderId: string,
  file: File,
  fileIndex: number,
  fileTotal: number,
  onProgress?: (progress: UploadProgress) => void,
  entityTag?: string
): Promise<UploadResult> {
  const total = Math.max(1, fileTotal);
  return uploadFileToFolderTracked(folderId, file, entityTag, (update) => {
    const overallPercent = ((fileIndex + update.ratio) / total) * 100;
    onProgress?.({
      percent: Math.round(Math.min(100, overallPercent)),
      fileIndex: fileIndex + 1,
      fileTotal: total,
      fileName: update.fileName,
      phase: update.phase,
    });
  });
}

export async function renameFile(oldPath: string, newName: string): Promise<void> {
  const response = await apiFetch(`${BASE_URL}/api/data/files/rename`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ oldPath, newName })
  });
  
  if (!response.ok) throw new Error("Failed to rename file");
}

export async function moveFiles(filePaths: string[], targetFolderId: string): Promise<void> {
  const response = await apiFetch(`${BASE_URL}/api/data/files/move`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ filePaths, targetFolderId })
  });
  
  if (!response.ok) throw new Error("Failed to move files");
}

export async function deleteFiles(filePaths: string[]): Promise<void> {
  const response = await apiFetch(`${BASE_URL}/api/data/files/delete`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ filePaths })
  });

  if (!response.ok) throw new Error("Failed to delete files");
}

export async function clearAllData(): Promise<void> {
  const response = await apiFetch(`${BASE_URL}/api/data/clear-all`, {
    method: "DELETE"
  });

  if (!response.ok) throw new Error("Failed to clear all data");
}

export async function getMessagesForChat(chatId: string): Promise<Message[]> {
  const response = await apiFetch(`${BASE_URL}/api/chat/${chatId}/messages`);
  if (!response.ok) throw new Error("Failed to fetch messages");
  const messages: ApiMessage[] = await response.json();
  
  return messages.map((m, index) => ({
    id: `${chatId}-${index}`,
    role: m.type === "USER" ? "user" : "assistant",
    content: m.text,
    sources: m.sources,
    uncertain: m.uncertain ?? false,
    evidence: m.evidence,
    answerKind: m.answerKind,
  }));
}

export async function sendMessage(chatId: string, content: string): Promise<Message> {
  const response = await apiFetch(`${BASE_URL}/api/chat/${chatId}/send`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ message: content })
  });
  
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.detail || error?.error || `Failed to send message (${response.status})`);
  }
  const data = await response.json();
  
    return {
    id: `resp-${Date.now()}`,
    role: "assistant",
    content: data.response,
      sources: data.sources,
      uncertain: data.uncertain ?? false,
      evidence: data.evidence,
      answerKind: data.answerKind,
  };
}

export async function renameChat(chatId: string, newName: string): Promise<void> {
  const response = await apiFetch(`${BASE_URL}/api/chat/${chatId}/rename`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ newName })
  });
  
  if (!response.ok) throw new Error("Failed to rename chat");
}

export async function getFileEmbeddings(path: string): Promise<{
  title: string;
  content: string;
  chunkCount: number;
}> {
  const params = new URLSearchParams({ path });
  const response = await apiFetch(
    `${BASE_URL}/api/data/files/embeddings?${params.toString()}`
  );
  if (!response.ok) throw new Error("Failed to fetch file embeddings");
  const data = await response.json();
  return {
    title: data.title,
    content: data.content,
    chunkCount: Number(data.chunkCount) || 0,
  };
}

export async function getFilePreview(path: string): Promise<FilePreview> {
  const params = new URLSearchParams({ path });
  const response = await apiFetch(`${BASE_URL}/api/data/files/preview?${params.toString()}`);
  if (!response.ok) throw new Error("Failed to fetch file preview");
  const data = await response.json();
  return { ...data, path: data.path ?? path };
}

export async function deleteChat(chatId: string): Promise<void> {
  const response = await apiFetch(`${BASE_URL}/api/chat/${chatId}`, {
    method: "DELETE"
  });
  
  if (!response.ok) throw new Error("Failed to delete chat");
}
