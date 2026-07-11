import {
  detectFacesForFile,
  EntityMention,
  getMentionsForFile,
  getPendingSuggestions,
} from "@/lib/knowledge-api";
import type { UploadResult } from "@/lib/api";
import type { IdentityReviewFile } from "@/components/knowledge/UploadIdentityPrompt";

function isGenericName(name: string): boolean {
  const lower = name.toLowerCase().trim();
  return (
    lower.startsWith("nieznana") ||
    lower.startsWith("nieznany") ||
    lower.includes("mężczyzna") ||
    lower.includes("mezczyzna") ||
    lower.includes("kobieta") ||
    lower.includes("dziewczyn") ||
    lower.includes("chłopak") ||
    lower.includes("chlopak") ||
    lower.startsWith("osoba ") ||
    lower === "osoba" ||
    lower === "postać" ||
    lower === "postac"
  );
}

function mentionNeedsReview(mention: EntityMention): boolean {
  if (mention.status === "REJECTED") {
    return false;
  }
  if (mention.status === "CONFIRMED") {
    const name = mention.entityDisplayName || mention.label;
    return isGenericName(name);
  }
  return true;
}

export async function buildIdentityReviewQueue(
  uploads: UploadResult[],
  imageUrlByPath: Map<string, string>
): Promise<IdentityReviewFile[]> {
  const imageUploads = uploads.filter((upload) => upload.image && upload.path);
  if (imageUploads.length === 0) {
    return [];
  }

  const pendingSuggestions = await getPendingSuggestions();
  const queue: IdentityReviewFile[] = [];

  for (const upload of imageUploads) {
    let mentions = await getMentionsForFile(upload.path);
    const needsFaceDetection = mentions.some(
      (mention) => !mention.bbox || mention.bbox.length < 4
    );
    if (mentions.length > 0 && needsFaceDetection) {
      try {
        mentions = await detectFacesForFile(upload.path);
      } catch {
        // Face service may be offline — still allow manual tagging.
      }
    }

    const fileSuggestions = pendingSuggestions.filter(
      (suggestion) =>
        suggestion.mentionA.filePath === upload.path ||
        suggestion.mentionB.filePath === upload.path
    );

    const needsReview =
      mentions.some(mentionNeedsReview) || fileSuggestions.length > 0;

    if (!needsReview) {
      continue;
    }

    queue.push({
      path: upload.path,
      fileName: upload.fileName,
      imageUrl: imageUrlByPath.get(upload.path) ?? "",
    });
  }

  return queue;
}
