const API_URL = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

export interface SuggestionMention {
  id: string;
  label: string;
  entityType?: string;
  filePath: string;
  fileName: string;
  faceCropBase64?: string | null;
}

export interface IdentitySuggestion {
  id: string;
  mentionA: SuggestionMention;
  mentionB: SuggestionMention;
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
  entityId?: string | null;
  entityDisplayName?: string | null;
  bbox?: number[] | null;
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

export interface EntityAppearance {
  mentionId: string;
  filePath: string;
  fileName: string;
  status: string;
  confidence: number;
  bbox?: number[] | null;
}

export interface PersonGraphNode {
  id: string;
  displayName: string;
  photoCount: number;
}

export interface PersonGraphEdge {
  sourceId: string;
  targetId: string;
  relation: string;
  weight: number;
  kind: "SPATIAL" | "CO_OCCURRENCE" | string;
}

export interface PersonRelationGraph {
  nodes: PersonGraphNode[];
  edges: PersonGraphEdge[];
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

export async function getEntity(entityId: string): Promise<KnowledgeEntity> {
  const res = await fetch(`${API_URL}/api/knowledge/entities/${entityId}`);
  if (!res.ok) throw new Error("Failed to fetch entity");
  return res.json();
}

export async function getEntityAppearances(entityId: string): Promise<EntityAppearance[]> {
  const res = await fetch(`${API_URL}/api/knowledge/entities/${entityId}/appearances`);
  if (!res.ok) throw new Error("Failed to fetch entity appearances");
  return res.json();
}

export async function getPersonRelationGraph(): Promise<PersonRelationGraph> {
  const res = await fetch(`${API_URL}/api/knowledge/graph/person-relations`);
  if (!res.ok) throw new Error("Failed to fetch person relation graph");
  return res.json();
}

export async function getMentionsForFile(path: string): Promise<EntityMention[]> {
  const params = new URLSearchParams({ path });
  const res = await fetch(`${API_URL}/api/knowledge/mentions/by-file?${params.toString()}`);
  if (!res.ok) throw new Error("Failed to fetch mentions for file");
  return res.json();
}

export async function detectFacesForFile(path: string): Promise<EntityMention[]> {
  const params = new URLSearchParams({ path });
  const res = await fetch(`${API_URL}/api/knowledge/mentions/by-file/detect-faces?${params.toString()}`, {
    method: "POST",
  });
  if (!res.ok) throw new Error("Failed to detect faces for file");
  return res.json();
}

export async function resolveFaceBatch(paths: string[]): Promise<{
  linked: number;
  clusters: number;
  unresolved: number;
}> {
  const res = await fetch(`${API_URL}/api/knowledge/faces/resolve-batch`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ paths }),
  });
  if (!res.ok) throw new Error("Failed to resolve face batch");
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
