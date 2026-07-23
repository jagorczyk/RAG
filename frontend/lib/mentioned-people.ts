import {
  getAllEntities,
  getEntity,
  getMentionsForFile,
  type KnowledgeEntity,
} from "./knowledge-api";
import type { Source } from "./api";

export type MentionedPerson = {
  id: string;
  displayName: string;
  /** Face crop (preferred) or fallback photo, JPEG base64 without data-URL prefix. */
  photoBase64?: string;
};

function isPerson(entity: KnowledgeEntity): boolean {
  return (entity.type || "").toUpperCase() === "PERSON";
}

function nameInAnswer(answer: string, displayName: string): boolean {
  const name = displayName.trim();
  if (!name || name.length < 2) return false;
  const hay = answer.toLocaleLowerCase("pl");
  const needle = name.toLocaleLowerCase("pl");
  const idx = hay.indexOf(needle);
  if (idx < 0) return false;
  const before = idx === 0 ? " " : hay[idx - 1];
  const after = idx + needle.length >= hay.length ? " " : hay[idx + needle.length];
  return !/\p{L}/u.test(before) && !/\p{L}/u.test(after);
}

async function attachFaceCrops(people: MentionedPerson[]): Promise<MentionedPerson[]> {
  if (people.length === 0) return people;
  await Promise.all(
    people.map(async (person) => {
      try {
        const entity = await getEntity(person.id);
        if (entity.faceCropBase64) {
          person.photoBase64 = entity.faceCropBase64;
        }
      } catch {
        /* keep existing photo / initials */
      }
    })
  );
  return people;
}

/**
 * People supporting the answer: PERSON entities on attributed source photos,
 * preferring names that appear in the answer prose.
 */
export async function resolveMentionedPeople(
  answer: string,
  sources: Source[] | undefined
): Promise<MentionedPerson[]> {
  if (!sources?.length) return [];

  const byId = new Map<string, MentionedPerson>();
  const paths = [...new Set(sources.map((s) => s.path).filter(Boolean))].slice(0, 6);

  await Promise.all(
    paths.map(async (path) => {
      try {
        const mentions = await getMentionsForFile(path);
        for (const mention of mentions) {
          if (mention.status && mention.status !== "CONFIRMED") continue;
          const id = mention.entityId || mention.entity?.id;
          const displayName =
            mention.entityDisplayName?.trim() ||
            mention.entity?.displayName?.trim() ||
            mention.label?.trim();
          if (!id || !displayName) continue;
          const type = (mention.entity?.type || "PERSON").toUpperCase();
          if (type !== "PERSON") continue;
          if (!byId.has(id)) {
            byId.set(id, {
              id,
              displayName,
            });
          }
        }
      } catch {
        /* ignore per-path failures */
      }
    })
  );

  if (byId.size === 0) {
    try {
      const entities = await getAllEntities();
      const sourcePaths = new Set(paths);
      for (const entity of entities) {
        if (!isPerson(entity)) continue;
        const onSource = entity.photos?.some((p) => sourcePaths.has(p.path));
        if (!onSource && !nameInAnswer(answer, entity.displayName)) continue;
        if (onSource || nameInAnswer(answer, entity.displayName)) {
          byId.set(entity.id, {
            id: entity.id,
            displayName: entity.displayName,
            photoBase64: entity.faceCropBase64 || entity.photos?.[0]?.imageBase64,
          });
        }
      }
    } catch {
      return [];
    }
  }

  const all = [...byId.values()];
  const named = all.filter((p) => nameInAnswer(answer, p.displayName));
  const selected = (named.length > 0 ? named : all).slice(0, 8);
  return attachFaceCrops(selected);
}
