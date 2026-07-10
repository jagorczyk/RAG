const API_URL = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

export interface IdentitySuggestion {
  id: string;
  mentionA: EntityMention;
  mentionB: EntityMention;
  similarityScore: number;
  status: string;
}

export interface EntityMention {
  id: string;
  filePath: string;
  label: string;
  confidence: number;
  status: string;
  visualCues: string;
  entity?: KnowledgeEntity;
}

export interface EntityPhoto {
  path: string;
  fileName: string;
  imageBase64: string;
  fileType: string;
}

export interface KnowledgeEntity {
  id: string;
  displayName: string;
  type: string;
  photos?: EntityPhoto[];
}

export async function getPendingSuggestions(): Promise<IdentitySuggestion[]> {
  const res = await fetch(`${API_URL}/api/knowledge/review/pending`);
  if (!res.ok) throw new Error("Failed to fetch pending suggestions");
  return res.json();
}

export async function confirmMention(mentionId: string, entityId: string): Promise<void> {
  const res = await fetch(`${API_URL}/api/knowledge/mentions/${mentionId}/confirm?entityId=${entityId}`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to confirm mention");
}

export async function rejectMention(mentionId: string): Promise<void> {
  const res = await fetch(`${API_URL}/api/knowledge/mentions/${mentionId}/reject`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to reject mention");
}

export async function mergeSuggestion(suggestionId: string): Promise<void> {
  const res = await fetch(`${API_URL}/api/knowledge/suggestions/${suggestionId}/merge`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to merge suggestion");
}

export async function splitSuggestion(suggestionId: string): Promise<void> {
  const res = await fetch(`${API_URL}/api/knowledge/suggestions/${suggestionId}/split`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to split suggestion");
}

export async function getAllEntities(): Promise<KnowledgeEntity[]> {
  const res = await fetch(`${API_URL}/api/knowledge/entities`);
  if (!res.ok) throw new Error("Failed to fetch entities");
  return res.json();
}

export async function getMentionsForFile(path: string): Promise<EntityMention[]> {
  const params = new URLSearchParams({ path });
  const res = await fetch(`${API_URL}/api/knowledge/mentions/by-file?${params.toString()}`);
  if (!res.ok) throw new Error("Failed to fetch mentions for file");
  return res.json();
}

export async function renameEntity(entityId: string, newName: string): Promise<void> {
  const params = new URLSearchParams({ newName });
  const res = await fetch(`${API_URL}/api/knowledge/entities/${entityId}/rename?${params.toString()}`, { method: "PUT" });
  if (!res.ok) throw new Error("Failed to rename entity");
}

export async function renameMention(mentionId: string, newName: string): Promise<void> {
  const params = new URLSearchParams({ newName });
  const res = await fetch(`${API_URL}/api/knowledge/mentions/${mentionId}/rename?${params.toString()}`, { method: "PUT" });
  if (!res.ok) throw new Error("Failed to rename mention");
}
