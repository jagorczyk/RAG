# Zasady systemowe RAG / GraphRAG

Dokument wiążący dla każdego agenta i każdej zmiany w repozytorium.
Plany techniczne (`docs/graphrag-plan.md`, `docs/roadmap.md`) opisują architekturę;
ten plik definiuje **normy decyzyjne**: co wolno, czego nie wolno i jak ma
zachowywać się system.

`frontend/AGENTS.md` dotyczy wyłącznie konwencji Next.js — nie umieszczaj tu
reguł frameworka frontendu.

---

## Dynamiczna interpretacja zapytań

W całym projekcie nie wolno implementować logiki odpowiedzi przez statyczne
warunki zależne od treści użytkownika. Zakazane są w szczególności listy imion,
atrybutów, kolorów, ubrań, czynności, relacji oraz regexy i `contains` służące
do rozpoznawania intencji pytania. System ma interpretować prośbę z bieżącego
kontekstu rozmowy, danych w bazie i dowodów pobranych przez RAG.

Dozwolone są wyłącznie warunki techniczne: walidacja danych wejściowych,
bezpieczeństwo, obsługa błędów, wybór parsera na podstawie MIME oraz
konfigurowalne limity i progi pewności. Nowe możliwości dodawaj jako dane,
otwarte fakty lub narzędzia techniczne planera, nie jako specjalne gałęzie dla
konkretnych sformułowań pytań.

---

## Zasady produktowe (1–7)

### 1. Hybrydowe wyszukiwanie RAG

Retrieval łączy **semantykę** (embedding / pgvector) i **leksykę** (dopasowanie
słów, nazwy plików, fragmenty tekstu, aliasy encji).

- Tryb planera `HYBRID` oznacza świadome łączenie sygnałów, nie samo
  „vector top-k”.
- Przy zmianach w retrieval dąż do domknięcia obu ścieżek; nie zastępuj
  hybrydy samym embeddingiem.

**Stan kodu:** dominuje search wektorowy (`RetrievalConfiguration`) oraz
kontekst grafu; pełna ścieżka lexical/FTS/BM25 może być jeszcze niepełna —
kolejne prace retrieval mają tę lukę domykać, nie pogłębiać.

### 2. GraphRAG zwraca wyłącznie pewne źródła

Do listy źródeł i do odpowiedzi opartej na grafie trafiają wyłącznie dowody
**pewne**, na przykład:

- decyzja wizualna `MATCH` powyżej skonfigurowanego progu,
- wzmianki i fakty o wystarczającej pewności,
- status potwierdzony tam, gdzie tożsamość ma znaczenie.

Niepewne wyniki: flaga `uncertain` lub komunikat o braku potwierdzenia —
**bez promocji** do pewnego źródła.

**Stan kodu:** `DynamicVisualMatcher` filtruje do `MATCH` z progami;
`GraphQueryService.isUsableMention` nadal dopuszcza m.in. `SUGGESTED`. Przy
zmianach w grafie preferuj **zaostrzanie** (tylko pewne źródła), nie
rozluźnianie.

### 3. GraphRAG opisuje uczestników zdjęcia i relacje

Rdzeń grafu dla obrazów to:

- **kto** jest na zdjęciu (encje żywe: osoby, zwierzęta),
- **relacje** między nimi oraz z obiektami sceny (przestrzenne i interakcje),

a nie luźny esej tekstowy. Dane trafiają do encji, wzmianek, faktów i relacji.

### 4. Każde zdjęcie jako struktura JSON

Każdy obraz jest opisywany strukturalnie (zgodnie z `VisionResultDto` i
`vision.structured.prompt`), w tym:

- lista osób / encji na zdjęciu,
- opis kontekstu sceny (`scene`, `scene_summary`),
- relacje między osobami (np. z lewej, z prawej, obok),
- szczegóły wspierające przyszłe pytania (`visual_cues`, obiekty, napisy, bbox).

Wzorcowy kształt:

```json
{
  "entities": [
    {
      "label": "person 1",
      "type": "PERSON",
      "actions": [],
      "objects": [],
      "visual_cues": [],
      "bbox": []
    }
  ],
  "relations": [
    {
      "subject_label": "person 1",
      "relation": "z lewej od",
      "object_label": "person 2"
    }
  ],
  "scene": "...",
  "scene_summary": "...",
  "visible_texts": []
}
```

Vision **nie zgaduje** tożsamości ani imion — używa stabilnych etykiet
(`person 1`). Imiona i tożsamość pochodzą z tagów użytkownika, face-match i
identity resolution.

### 5. Maksymalna szczegółowość opisu → embeddingi

Opis zdjęcia ma być **jak najdokładniejszy**: wygląd, ubiór, kolory, czynności,
obiekty, czytelne napisy, układ przestrzenny, tło i inne widoczne fakty.

Każdy istotny szczegół musi trafić do:

- tekstu kanonicznego embeddowanego przy ingeście (`IngestionService`),
- faktów i relacji w grafie,

a nie ginąć wyłącznie w UI. Zmiany promptu vision lub serializacji JSON mają
**zwiększać** recall szczegółów, nie skracać opisu „dla zwięzłości ingestu”.
Nie wolno wymyślać szczegółów niewidocznych na obrazie.

### 6. GraphRAG odpowiada po polsku na logicznie sformułowane pytania

- Język odpowiedzi i komunikatów UX: **polski**.
- System ma obsłużyć każde **logicznie sformułowane** pytanie o zdjęcia i relacje
  na podstawie planera, bazy i dowodów RAG — bez zamkniętego słownika pytań.
- Brak dowodów → jasna odpowiedź o braku informacji, bez halucynacji.

### 7. Prosta odpowiedź; źródła tylko na liście źródeł

- Treść odpowiedzi modelu: **krótka i prosta**, bez ścieżek, nazw plików i
  cytowań technicznych.
- Źródła wyłącznie w polu/listie `sources` (API: `SourceDto`; UI: lista/bąble
  źródeł), spójnie z `ChatService.ANSWER_INSTRUCTIONS` i
  `ChatInteractionService.removeTechnicalReferences`.
- Nie dubluj źródeł w prozie modelu (np. „jak widać na pliku X.jpg…”).

---

## Mapowanie zasad na kod

| Zasada | Główne miejsca |
|---|---|
| 1 Hybrid | `RetrievalConfiguration`, `ImageCandidateRetriever`, `QueryPlan` |
| 2 Pewne źródła | `DynamicVisualMatcher`, `GraphQueryService`, `ChatInteractionService` |
| 3–5 Opis / JSON / embedding | `StructuredVisionExtractor`, `VisionResultDto`, `ExtractedRelationDto`, `IngestionService`, `vision.structured.prompt` |
| 6 PL + otwarte pytania | `ChatService`, `QueryPlanner`, `VerifiedVisualAnswerService` |
| 7 Źródła poza odpowiedzią | `ChatService.ANSWER_INSTRUCTIONS`, `removeTechnicalReferences`, komponenty czatu frontendu |

---

## Wolno / nie wolno

**Wolno**

- konfigurowalne progi pewności i limity w `application.properties`,
- filtry statusów wzmianek i decyzji wizualnych,
- narzędzia techniczne planera zapytań,
- rozszerzanie schematu JSON vision o **otwarte** pola (bez słowników domenowych),
- dodawanie retrieval leksykalnego jako uzupełnienie wektorowego.

**Nie wolno**

- hardcodowanych list imion, kolorów, ubrań, czynności do routingu pytań,
- traktowania niepewnych wzmianek/dowodów jako pewnych źródeł GraphRAG,
- wstawiania `@plik`, ścieżek i nazw plików do treści odpowiedzi modelu,
- upraszczania JSON vision lub tekstu kanonicznego kosztem szczegółów potrzebnych w embeddingach,
- zamykania obsługi pytań do sztywnego katalogu fraz.

---

## Luki względem kodu (jawnie)

Agenci nie mogą udawać, że poniższe jest już w pełni domknięte:

1. **Lexical retrieval** — zasada 1 wymaga hybrydy; dziś silniejsza jest ścieżka wektorowa.
2. **Pewność wzmianek w grafie** — zasada 2 preferuje wyłącznie pewne źródła;
   `isUsableMention` nadal może uwzględniać `SUGGESTED`. Kolejne zmiany mają
   zaostrzać, nie rozluźniać.

Przy implementacji luk zachowaj regułę dynamicznej interpretacji i zasady 3–7.
