#Requires -Version 5.1
<#
.SYNOPSIS
    Tworzy etykiety i issue na GitHubie na podstawie docs/issues-backlog.md

.PREREQUISITE
    gh auth login
#>

$ErrorActionPreference = "Stop"

function Ensure-GhAuth {
    gh auth status 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Brak logowania do GitHub. Uruchom: gh auth login"
    }
}

function Ensure-Label {
    param([string]$Name, [string]$Color, [string]$Description)
    gh label create $Name --color $Color --description $Description --force 2>$null | Out-Null
}

function New-GhIssue {
    param(
        [string]$Title,
        [string]$Body,
        [string[]]$Labels
    )
    $bodyFile = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($bodyFile, $Body, [System.Text.UTF8Encoding]::new($false))
        $labelArgs = $Labels | ForEach-Object { "--label", $_ }
        gh issue create --title $Title --body-file $bodyFile @labelArgs
    } finally {
        Remove-Item $bodyFile -ErrorAction SilentlyContinue
    }
}

Ensure-GhAuth

Write-Host "Tworzenie etykiet..."
Ensure-Label "add" "0E8A16" "Nowa funkcja"
Ensure-Label "improve" "1D76DB" "Poprawka lub usprawnienie"
Ensure-Label "remove" "D93F0B" "Sprzatanie / deprecacja"
Ensure-Label "graphrag" "5319E7" "Graf wiedzy / GraphRAG"
Ensure-Label "tech-debt" "FBCA04" "Dlug techniczny"
Ensure-Label "priority-high" "B60205" "Wysoki priorytet"
Ensure-Label "priority-medium" "E99695" "Sredni priorytet"
Ensure-Label "priority-low" "C5DEF5" "Niski priorytet"

$issues = @(
    @{
        Title = "[ADD] Typ zrodla GRAPH_FACT i banner niepewnosci w czacie"
        Labels = @("add", "graphrag", "priority-medium")
        Body = @"
## Opis
Plan GraphRAG Faza 6d — odróżnianie źródeł grafowych od dokumentów i informowanie użytkownika o niskiej pewności.

## Zakres
- Dodać typ ``GRAPH_FACT`` do ``SourceDto``
- Przekazywać ``confidence`` z faktów grafu do frontendu
- Banner „Odpowiedź może być niepełna" przy niskiej pewności

## Pliki
- ``backend/src/main/java/com/rag/rag/ingestion/dto/SourceDto.java``
- ``backend/src/main/java/com/rag/rag/chat/service/ChatInteractionService.java``
- ``frontend/components/chat/ChatMessageBubble.tsx``

## Kryteria akceptacji
- [ ] Źródła z grafu mają osobny typ w UI
- [ ] Przy confidence < progu pokazuje się ostrzeżenie
"@
    },
    @{
        Title = "[ADD] Wizualizacja grafu wiedzy w UI"
        Labels = @("add", "graphrag", "priority-low")
        Body = @"
## Opis
Panel lub strona wizualizująca encje i relacje (współwystępowanie, NEXT_TO, LEFT_OF, RIGHT_OF).

## Zakres
- Nowy widok w frontend (np. ``/knowledge/graph``)
- API agregujące dane z ``GraphQueryService``
- Prosta wizualizacja (np. lista relacji lub graf SVG/D3)

## Kryteria akceptacji
- [ ] Użytkownik widzi powiązania między osobami z bazy wiedzy
"@
    },
    @{
        Title = "[ADD] Status zdrowia face-service w UI"
        Labels = @("add", "graphrag", "priority-medium")
        Body = @"
## Opis
``FaceRecognitionClient.isHealthy()`` istnieje, ale nie jest nigdzie używany.

## Zakres
- Spring ``HealthIndicator`` dla face-service
- Wskaźnik w panelu „Osoby" (online/offline)

## Pliki
- ``backend/src/main/java/com/rag/rag/knowledge/identity/FaceRecognitionClient.java``
- ``frontend/components/knowledge/EntitiesPanel.tsx``

## Kryteria akceptacji
- [ ] UI pokazuje czy dopasowanie twarzy jest aktywne
"@
    },
    @{
        Title = "[ADD] Czyszczenie osieroconych encji po usunieciu plikow"
        Labels = @("add", "graphrag", "priority-medium")
        Body = @"
## Opis
``IngestionService.deleteFiles()`` usuwa mentions/facts/embeddingi, ale ``KnowledgeEntity`` bez mentions pozostają w bazie.

## Zakres
- Po usunięciu pliku: usuń encje bez żadnych mentions
- Opcjonalnie: endpoint do ręcznego sprzątania

## Kryteria akceptacji
- [ ] Usunięcie ostatniego pliku z encją usuwa encję
- [ ] Test jednostkowy pokrywa cascade
"@
    },
    @{
        Title = "[ADD] LLM fallback dla QueryRouter"
        Labels = @("add", "graphrag", "priority-low")
        Body = @"
## Opis
``QueryRouter`` opiera się wyłącznie na regexach PL. Dodać opcjonalny fallback LLM dla zapytań nietrafionych heurystykami.

## Zakres
- Klasyfikacja ``QueryRoute`` przez LLM gdy regex → DOCUMENT
- Konfiguracja w ``application.properties`` (włącz/wyłącz)

## Kryteria akceptacji
- [ ] Nietypowe pytania o osoby trafiają do grafu zamiast czystego vector RAG
"@
    },
    @{
        Title = "[ADD] Filtrowanie HYBRID po folderze lub sciezce"
        Labels = @("add", "graphrag", "priority-medium")
        Body = @"
## Opis
Plan: „co robi A w folderze Wakacje?" — graf + filtr path.

## Zakres
- Rozszerzyć ``GraphQueryService`` o filtr ``file_path``
- Router HYBRID rozpoznaje kontekst folderu w pytaniu

## Kryteria akceptacji
- [ ] Pytanie z nazwą folderu zawęża fakty do tego folderu
"@
    },
    @{
        Title = "[ADD] Edycja aliasow encji w UI"
        Labels = @("add", "graphrag", "priority-medium")
        Body = @"
## Opis
API aliasów istnieje, brak UI do zarządzania.

## Zakres
- Formularz dodawania/usuwania aliasów w ``EntitiesPanel``
- Integracja z ``/api/knowledge/entities/{id}/aliases``

## Kryteria akceptacji
- [ ] Użytkownik może dodać alias „Bartek" do encji „Bartłomiej"
"@
    },
    @{
        Title = "[BUG] entityTag przy uploadzie nie dziala"
        Labels = @("improve", "graphrag", "bug", "priority-high")
        Body = @"
## Opis
Frontend wysyła ``?entityTag=``, UI ma pole, ale backend nie odbiera parametru.

## Problem
- ``FolderController.upload()`` nie ma ``@RequestParam entityTag``
- ``IngestionService.ingestMultipartFile()`` nie ustawia ``FileEntity.entityTag``
- Ścieżka L1 w ``IdentityResolutionService.resolve()`` nigdy się nie wykonuje

## Pliki
- ``backend/.../folder/FolderController.java``
- ``backend/.../ingestion/service/IngestionService.java``
- ``frontend/lib/api.ts``
- ``frontend/app/folders/[id]/page.tsx``

## Kryteria akceptacji
- [ ] Upload z tagiem „A" tworzy encję CONFIRMED
- [ ] Test integracyjny upload + identity resolution
"@
    },
    @{
        Title = "[IMPROVE] Wydajnosc IdentityResolutionService O(n^2) + LLM"
        Labels = @("improve", "graphrag", "tech-debt", "priority-high")
        Body = @"
## Opis
``resolve()`` iteruje ``mentionRepository.findAll()`` i wywołuje LLM per para kandydatów.

## Zakres
- Pre-filtr kandydatów (label, folder, face embedding)
- Cache wyników matchera LLM
- Ograniczenie liczby porównań

## Pliki
- ``backend/.../knowledge/identity/IdentityResolutionService.java``

## Kryteria akceptacji
- [ ] Ingest 10+ zdjęć nie powoduje setek wywołań LLM
"@
    },
    @{
        Title = "[IMPROVE] Efekt uboczny na GET /api/knowledge/entities"
        Labels = @("improve", "graphrag", "tech-debt", "priority-medium")
        Body = @"
## Opis
``getAllEntities()`` wywołuje ``consolidateDuplicateEntities()`` (zapis/merge) przy każdym odczycie + N+1.

## Zakres
- Konsolidacja tylko przez ``/entities/consolidate-duplicates``
- Optymalizacja zapytań (JOIN zamiast N+1)

## Kryteria akceptacji
- [ ] GET entities jest read-only i szybki
"@
    },
    @{
        Title = "[IMPROVE] Ryzyko blednego auto-merge po samym labelu"
        Labels = @("improve", "graphrag", "bug", "priority-high")
        Body = @"
## Opis
Identyczny label → score 0.95 bez weryfikacji twarzy. Dwie różne osoby o tym samym opisie mogą zostać połączone.

## Zakres
- Wymagać potwierdzenia gdy brak zgodności face embedding
- Obniżyć próg auto-merge bez twarzy

## Kryteria akceptacji
- [ ] Kryterium akceptacyjne #2 planu: podobne osoby bez tagu nie są auto-łączone bez dowodu
"@
    },
    @{
        Title = "[IMPROVE] Ujednolicic progi face-service i dokumentacje"
        Labels = @("improve", "priority-low")
        Body = @"
## Opis
``face-service/README.md`` podaje próg 0.42, ``application.properties`` używa 0.55.

## Zakres
- Ujednolicić wartości i opis w README + properties
- Udokumentować ``min-margin``, ``min-det-score``, ``suggestion``
"@
    },
    @{
        Title = "[IMPROVE] Brakujace testy jednostkowe Faza 7"
        Labels = @("improve", "graphrag", "tech-debt", "priority-high")
        Body = @"
## Opis
Plan Faza 7 — brak testów kluczowych modułów.

## Zakres
- ``StructuredVisionExtractor`` — JSON + fallback
- ``IdentityResolutionService`` — progi 0.85/0.60
- ``GraphQueryService`` — agregacja, co-occurrence, spatial
- ``ChatEntityReferenceService``, ``PolishNameMatcher``
- E2E: delete pliku → cascade mentions/facts

## Kryteria akceptacji
- [ ] Pokrycie krytycznej ścieżki GraphRAG testami
"@
    },
    @{
        Title = "[IMPROVE] Zaktualizowac README.md do rzeczywistej implementacji"
        Labels = @("improve", "priority-medium")
        Body = @"
## Opis
README nie odzwierciedla aktualnego stanu projektu.

## Rozjazdy
- Modele: Groq vs Ollama w README vs properties
- Brak GraphRAG, face-service, ``/api/knowledge/*``
- ``DELETE /api/data/clear`` vs ``/api/data/clear-all``
- Brak ``POST /api/data/files/delete``

## Kryteria akceptacji
- [ ] README opisuje GraphRAG, twarze, knowledge API
- [ ] Instrukcja uruchomienia face-service
"@
    },
    @{
        Title = "[IMPROVE] Zduplikowany REFERENCE_PATTERN w QueryRouter i ChatEntityReferenceService"
        Labels = @("improve", "tech-debt", "priority-low")
        Body = @"
## Opis
Ten sam regex w dwóch klasach — ryzyko rozjazdu przy zmianach.

## Zakres
- Wydzielić do ``GraphQueryPatterns`` lub ``PolishQueryPatterns``

## Kryteria akceptacji
- [ ] Jedna definicja wzorca, oba serwisy importują
"@
    },
    @{
        Title = "[IMPROVE] Niespojna konfiguracja maxResults retrieval"
        Labels = @("improve", "tech-debt", "priority-low")
        Body = @"
## Opis
Default 20 w ``@Value``, 5 w properties, hardcoded 40/15 w ``RetrievalConfiguration``.

## Zakres
- Sparametryzować wszystkie limity w ``application.properties``
- Usunąć magic numbers
"@
    },
    @{
        Title = "[IMPROVE] Lepszy fallback gdy encja nie zostala znaleziona w grafie"
        Labels = @("improve", "graphrag", "priority-medium")
        Body = @"
## Opis
Gdy router trafia w ENTITY_*, ale encja nie istnieje — pusty kontekst grafu, słaba odpowiedź.

## Zakres
- Fallback do vector RAG z komunikatem
- Logowanie nietrafionych tras

## Kryteria akceptacji
- [ ] Pytanie o nieistniejącą osobę daje sensowną odpowiedź zamiast błędu/pustki
"@
    },
    @{
        Title = "[REMOVE] Martwy kod visionModel i VISION_PROMPT w IngestionService"
        Labels = @("remove", "tech-debt", "priority-medium")
        Body = @"
## Opis
``VISION_PROMPT`` i ``visionModel`` nie są używane — zastąpione przez ``StructuredVisionExtractor``.

## Zakres
- Usunąć pole, import, ``vision.prompt`` z properties
"@
    },
    @{
        Title = "[REMOVE] Zdublowane importy w IngestionService"
        Labels = @("remove", "tech-debt", "priority-low")
        Body = @"
## Opis
Duplikaty importów w ``IngestionService.java`` (~linie 45–71).
"@
    },
    @{
        Title = "[REMOVE] Ujednolicic mylacy endpoint DELETE /api/data/clear"
        Labels = @("remove", "tech-debt", "priority-medium")
        Body = @"
## Opis
``clearEmbeddingsTable()`` tylko TRUNCATE embeddings, zostawia osierocone files i graf. Realne czyszczenie: ``/api/data/clear-all``.

## Zakres
- Usunąć lub rozgraniczyć endpointy w API i README
"@
    },
    @{
        Title = "[REMOVE] Rozszerzyc .gitignore o artefakty build i pliki lokalne"
        Labels = @("remove", "tech-debt", "priority-high")
        Body = @"
## Opis
Setki nieśledzonych plików: ``backend/target/``, ``frontend/.next/``, LaTeX, PDF-y, ``.impeccable/``.

## Zakres
- Zaktualizować ``.gitignore``
- Nie commitować artefaktów budowania i pracy dyplomowej
"@
    },
    @{
        Title = "[REMOVE] Usunac przypadkowe pliki z repozytorium"
        Labels = @("remove", "tech-debt", "priority-medium")
        Body = @"
## Opis
Kandydaci: ``fix_listings.py``, ``revert_listings.py``, ``test-data/img.b64``, lokalne PDF-y.

## Zakres
- Zweryfikować z właścicielem
- Usunąć lub przenieść poza repo
"@
    }
)

Write-Host "Tworzenie $($issues.Count) issue..."
foreach ($issue in $issues) {
    Write-Host "  -> $($issue.Title)"
    New-GhIssue -Title $issue.Title -Body $issue.Body -Labels $issue.Labels
}

Write-Host ""
Write-Host "Gotowe. Lista issue:"
gh issue list --limit 30
