import { apiFetch } from "./auth";

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
  identityConfidence?: number | null;
  identityMargin?: number | null;
  identitySource?: "USER" | "USER_TAG" | "FACE_MATCH" | "DESCRIPTION_MATCH" | "FACE_CLUSTER" | null;
  status: string;
  visualCues: string;
  entity?: KnowledgeEntity;
  entityId?: string | null;
  entityDisplayName?: string | null;
  bbox?: number[] | null;
  bboxSource?: "FACE" | "VISION" | null;
}

export function hasFaceBbox(mention: EntityMention): boolean {
  return mention.bboxSource === "FACE" && !!mention.bbox && mention.bbox.length >= 4;
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
  faceCropBase64?: string | null;
}

export interface EntityAppearance {
  mentionId: string;
  filePath: string;
  fileName: string;
  status: string;
  confidence: number;
  bbox?: number[] | null;
  bboxSource?: "FACE" | "VISION" | null;
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

export class IdentityConflictError extends Error {
  constructor(
    public readonly code: string,
    public readonly existingMentionId?: string
  ) {
    super("Ta osoba jest już przypisana do innej twarzy na tym obrazie.");
  }
}

async function ensureIdentityResponse(res: Response, fallback: string): Promise<void> {
  if (res.status === 409) {
    const conflict = await res.json().catch(() => ({}));
    throw new IdentityConflictError(conflict.code ?? "ENTITY_ALREADY_ON_FILE", conflict.existingMentionId);
  }
  if (!res.ok) throw new Error(fallback);
}

export async function getPendingSuggestions(): Promise<IdentitySuggestion[]> {
  const res = await apiFetch(`${API_URL}/api/knowledge/review/pending`);
  if (!res.ok) throw new Error("Failed to fetch pending suggestions");
  return res.json();
}

export async function confirmMention(mentionId: string, entityId: string, allowDuplicateOnFile = false): Promise<void> {
  const params = new URLSearchParams({ entityId, allowDuplicateOnFile: String(allowDuplicateOnFile) });
  const res = await apiFetch(`${API_URL}/api/knowledge/mentions/${mentionId}/confirm?${params.toString()}`, { method: "POST" });
  await ensureIdentityResponse(res, "Failed to confirm mention");
}

export async function rejectMention(mentionId: string): Promise<void> {
  const res = await apiFetch(`${API_URL}/api/knowledge/mentions/${mentionId}/reject`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to reject mention");
}

export async function mergeSuggestion(suggestionId: string, allowDuplicateOnFile = false): Promise<void> {
  const params = new URLSearchParams({ allowDuplicateOnFile: String(allowDuplicateOnFile) });
  const res = await apiFetch(`${API_URL}/api/knowledge/suggestions/${suggestionId}/merge?${params.toString()}`, { method: "POST" });
  await ensureIdentityResponse(res, "Failed to merge suggestion");
}

export async function splitSuggestion(suggestionId: string): Promise<void> {
  const res = await apiFetch(`${API_URL}/api/knowledge/suggestions/${suggestionId}/split`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to split suggestion");
}

export async function getAllEntities(): Promise<KnowledgeEntity[]> {
  const res = await apiFetch(`${API_URL}/api/knowledge/entities`);
  if (!res.ok) throw new Error("Failed to fetch entities");
  return res.json();
}

export async function getEntity(entityId: string): Promise<KnowledgeEntity> {
  const res = await apiFetch(`${API_URL}/api/knowledge/entities/${entityId}`);
  if (!res.ok) throw new Error("Failed to fetch entity");
  return res.json();
}

export async function getEntityAppearances(entityId: string): Promise<EntityAppearance[]> {
  const res = await apiFetch(`${API_URL}/api/knowledge/entities/${entityId}/appearances`);
  if (!res.ok) throw new Error("Failed to fetch entity appearances");
  return res.json();
}

export async function getPersonRelationGraph(): Promise<PersonRelationGraph> {
  const res = await apiFetch(`${API_URL}/api/knowledge/graph/person-relations`);
  if (!res.ok) throw new Error("Failed to fetch person relation graph");
  return res.json();
}

export async function getMentionsForFile(path: string): Promise<EntityMention[]> {
  const params = new URLSearchParams({ path });
  const res = await apiFetch(`${API_URL}/api/knowledge/mentions/by-file?${params.toString()}`);
  if (!res.ok) throw new Error("Failed to fetch mentions for file");
  return res.json();
}

export async function detectFacesForFile(path: string): Promise<EntityMention[]> {
  const params = new URLSearchParams({ path });
  const res = await apiFetch(`${API_URL}/api/knowledge/mentions/by-file/detect-faces?${params.toString()}`, {
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
  const res = await apiFetch(`${API_URL}/api/knowledge/faces/resolve-batch`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ paths }),
  });
  if (!res.ok) throw new Error("Failed to resolve face batch");
  return res.json();
}

export async function renameEntity(entityId: string, newName: string): Promise<void> {
  const params = new URLSearchParams({ newName });
  const res = await apiFetch(`${API_URL}/api/knowledge/entities/${entityId}/rename?${params.toString()}`, { method: "PUT" });
  if (!res.ok) throw new Error("Failed to rename entity");
}

export async function renameMention(mentionId: string, newName: string, allowDuplicateOnFile = false): Promise<void> {
  const params = new URLSearchParams({ newName, allowDuplicateOnFile: String(allowDuplicateOnFile) });
  const res = await apiFetch(`${API_URL}/api/knowledge/mentions/${mentionId}/rename?${params.toString()}`, { method: "PUT" });
  await ensureIdentityResponse(res, "Failed to rename mention");
}
