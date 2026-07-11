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

gh auth status 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Brak logowania. Uruchom: gh auth login"
}

$issues = @(
    @{
        Title = "[ROADMAP] Graf first dla pytan z @plik"
        Labels = @("add", "graphrag", "priority-high")
        Body = @"
## Faza B.1 — Roadmap

Patrz: docs/roadmap.md (Sprint 5)

## Opis
Pytanie z @nazwa_pliku powinno isc przez graf wiedzy (osoby, fakty, relacje na tym pliku) jako zrodlo prawdy, a vector RAG tylko jako uzupelnienie opisu sceny.

## Zakres
- Wykrycie @plik w pytaniu (juz jest resolveFilePathFromQuestion)
- Trasa GRAPH_FIRST lub rozszerzenie DOCUMENT z obowiazkowym kontekstem grafu
- LLM dostaje [Osoby z grafu wiedzy na pliku] + fakty przed fragmentami wektorowymi
- Zrodla w UI tylko z grafu dla pytan o tozsamosc

## Kryteria akceptacji
- [ ] @zdjecie + opisz kto na nim jest -> odpowiedz z imionami z grafu (np. Igor)
- [ ] Nie odpowiada tylko mężczyzna gdy encja jest podpisana
"@
    },
    @{
        Title = "[ROADMAP] Face-match CONFIRMED przy ingestii"
        Labels = @("add", "graphrag", "priority-high")
        Body = @"
## Faza A.4 — Roadmap

Patrz: docs/roadmap.md (Sprint 5)

## Opis
Po dopasowaniu twarzy przez face-service automatycznie linkuj mention do encji ze statusem CONFIRMED i dodaj alias.

## Zakres
- FaceIdentityService: po match >= threshold -> linkMention CONFIRMED
- Sugestia PENDING tylko gdy score w zakresie suggestion-threshold
- Test integracyjny ingest + face

## Kryteria akceptacji
- [ ] Upload zdjecia z znana twarza Igora -> mention CONFIRMED bez recznego tagu
"@
    },
    @{
        Title = "[ROADMAP] UI tag lub potwierdzenie twarzy przy uploadzie"
        Labels = @("add", "graphrag", "priority-medium")
        Body = @"
## Faza A.5 — Roadmap

Patrz: docs/roadmap.md (Sprint 6)

## Opis
Przy uploadzie bez entityTag i bez pewnego face-match pokaz prompt: kto to jest / potwierdz sugestie.

## Zakres
- Frontend: modal lub inline po uploadzie gdy status SUGGESTED
- Integracja z IdentityReviewPanel
- Opcjonalny wymuszony tag przed zakonczeniem ingestii

## Kryteria akceptacji
- [ ] Uzytkownik moze od razu podpisac osobe na zdjeciu bez przechodzenia do panelu Osoby
"@
    }
)

foreach ($issue in $issues) {
    Write-Host "Tworzenie: $($issue.Title)"
    New-IssueFromBody -Title $issue.Title -Body $issue.Body -Labels $issue.Labels
}
