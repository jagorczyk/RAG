$ErrorActionPreference = "Stop"

function New-IssueFromBody {
    param([string]$Title, [string]$Body, [string[]]$Labels)
    $bodyFile = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($bodyFile, $Body, [System.Text.UTF8Encoding]::new($false))
        $labelArgs = $Labels | ForEach-Object { "--label", $_ }
        gh issue create --title $Title --body-file $bodyFile @labelArgs
    } finally {
        Remove-Item $bodyFile -ErrorAction SilentlyContinue
    }
}

$missing = @(
    @{
        Title = "[ADD] Typ zrodla GRAPH_FACT i banner niepewnosci w czacie"
        Labels = @("add", "graphrag", "priority-medium")
        Body = @"
## Opis
Plan GraphRAG Faza 6d - odroznianie zrodel grafowych od dokumentow i informowanie uzytkownika o niskiej pewnosci.

## Zakres
- Dodac typ GRAPH_FACT do SourceDto
- Przekazywac confidence z faktow grafu do frontendu
- Banner gdy odpowiedz moze byc niepelna przy niskiej pewnosci

## Pliki
- backend/src/main/java/com/rag/rag/ingestion/dto/SourceDto.java
- backend/src/main/java/com/rag/rag/chat/service/ChatInteractionService.java
- frontend/components/chat/ChatMessageBubble.tsx

## Kryteria akceptacji
- [ ] Zrodla z grafu maja osobny typ w UI
- [ ] Przy confidence ponizej progu pokazuje sie ostrzezenie
"@
    },
    @{
        Title = "[ADD] Status zdrowia face-service w UI"
        Labels = @("add", "graphrag", "priority-medium")
        Body = @"
## Opis
FaceRecognitionClient.isHealthy() istnieje, ale nie jest nigdzie uzywany.

## Zakres
- Spring HealthIndicator dla face-service
- Wskaznik w panelu Osoby (online/offline)

## Pliki
- backend/src/main/java/com/rag/rag/knowledge/identity/FaceRecognitionClient.java
- frontend/components/knowledge/EntitiesPanel.tsx

## Kryteria akceptacji
- [ ] UI pokazuje czy dopasowanie twarzy jest aktywne
"@
    },
    @{
        Title = "[ADD] Filtrowanie HYBRID po folderze lub sciezce"
        Labels = @("add", "graphrag", "priority-medium")
        Body = @"
## Opis
Plan: co robi A w folderze Wakacje - graf + filtr path.

## Zakres
- Rozszerzyc GraphQueryService o filtr file_path
- Router HYBRID rozpoznaje kontekst folderu w pytaniu

## Kryteria akceptacji
- [ ] Pytanie z nazwa folderu zaweza fakty do tego folderu
"@
    },
    @{
        Title = "[ADD] Edycja aliasow encji w UI"
        Labels = @("add", "graphrag", "priority-medium")
        Body = @"
## Opis
API aliasow istnieje, brak UI do zarzadzania.

## Zakres
- Formularz dodawania/usuwania aliasow w EntitiesPanel
- Integracja z /api/knowledge/entities/{id}/aliases

## Kryteria akceptacji
- [ ] Uzytkownik moze dodac alias Bartek do encji Bartlomiej
"@
    },
    @{
        Title = "[BUG] entityTag przy uploadzie nie dziala"
        Labels = @("improve", "graphrag", "bug", "priority-high")
        Body = @"
## Opis
Frontend wysyla ?entityTag=, UI ma pole, ale backend nie odbiera parametru.

## Problem
- FolderController.upload() nie ma @RequestParam entityTag
- IngestionService.ingestMultipartFile() nie ustawia FileEntity.entityTag
- Sciezka L1 w IdentityResolutionService.resolve() nigdy sie nie wykonuje

## Pliki
- backend/.../folder/FolderController.java
- backend/.../ingestion/service/IngestionService.java
- frontend/lib/api.ts
- frontend/app/folders/[id]/page.tsx

## Kryteria akceptacji
- [ ] Upload z tagiem A tworzy encje CONFIRMED
- [ ] Test integracyjny upload + identity resolution
"@
    }
)

foreach ($issue in $missing) {
    Write-Host "Tworzenie: $($issue.Title)"
    New-IssueFromBody -Title $issue.Title -Body $issue.Body -Labels $issue.Labels
}

gh issue list --limit 25
