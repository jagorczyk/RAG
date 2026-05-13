# RAG (Retrieval-Augmented Generation) - System Zarzadzania Dokumentami

Projekt to aplikacja RAG (Retrieval-Augmented Generation) sluzaca do zarzadzania dokumentami, ich analizy oraz interakcji z nimi poprzez czat wykorzystujacy modele jezykowe. System wspiera dokumenty tekstowe, pliki PDF oraz obrazy, oferujac zaawansowane mozliwosci detekcji obiektow i tekstu na obrazach.

## Architektura i Stos Technologiczny

Projekt sklada sie z dwoch glownych czesci: backendu (Spring Boot) oraz frontendu (Next.js).

### Backend
*   **Jezyk:** Java 17
*   **Framework:** Spring Boot 4.0.2
*   **Baza danych:** PostgreSQL z rozszerzeniem PGVector (przechowywanie wektorow)
*   **AI/LLM:** LangChain4j, Ollama, OpenAI API (kompatybilne z Groq)
*   **Modele:** 
    *   Llama 3.1 (czat)
    *   MiniCPM-V (wizja)
    *   bge-m3 (osadzenia / embeddings)
    *   YOLOv8s (detekcja obiektow)
    *   DB_TD500_resnet50 (detekcja tekstu)
*   **Przetwarzanie dokumentow:** Apache PDFBox, Apache Tika, OpenCV, DJL (Deep Java Library)

### Frontend
*   **Framework:** Next.js 16.2.2, React 19
*   **Stylizacja:** TailwindCSS 4
*   **Glowne funkcjonalnosci:**
    *   Wysuwany panel boczny (dashboard) z lista konwersacji i nawigacja.
    *   Interfejs czatu do rozmow z asystentem na temat wgranych dokumentow.
    *   Zarzadzanie folderami i podglad plikow (ze wsparciem miniatur dla obrazow i ikon dla plikow tekstowych/PDF).

---

## Konfiguracja i Uruchomienie

### Wymagania wstepne
*   Docker i Docker Compose
*   Java 17 (dla backendu)
*   Maven
*   Node.js 20+ (dla frontendu)
*   Ollama (zainstalowane lokalnie lub w kontenerze)

### Krok 1: Uruchomienie bazy danych
W glownym katalogu projektu znajduje sie plik `docker-compose.yml`, ktory definiuje kontener bazy danych PostgreSQL z rozszerzeniem PGVector.
Uruchom baze danych za pomoca polecenia:
```bash
docker-compose up -d pgvector
```

### Krok 2: Konfiguracja Backendu
W katalogu `backend/` znajduje sie kod zrodlowy serwera.

1.  **Pobranie modeli:**
    Modele detekcji (YOLO i TextDetector) musza znalezc sie w katalogu `backend/models/`. Domyslne sciezki to:
    *   `models/yolov8s.onnx`
    *   `models/DB_TD500_resnet50.onnx`
2.  **Zmienne srodowiskowe:**
    Backend mozna skonfigurowac za pomoca pliku `backend/.env` (opcjonalny) lub bezposrednio w `backend/src/main/resources/application.properties`. Wymagane jest ustawienie klucza API dla OpenAI (lub Groq, zgodnie z konfiguracja URL):
    ```properties
    OPENAI_API_KEY=twoj_klucz_api
    ```
    Opcjonalnie dostosuj parametry bazy danych jesli zmieniono je w docker-compose:
    ```properties
    DB_URL=jdbc:postgresql://localhost:5433/vector_db
    DB_USERNAME=user
    DB_PASSWORD=password
    ```
3.  **Uruchomienie:**
    ```bash
    cd backend
    ./mvnw spring-boot:run
    ```

### Krok 3: Konfiguracja Frontendu
W katalogu `frontend/` znajduje sie kod aplikacji klienckiej.

1.  **Instalacja zaleznosci:**
    ```bash
    cd frontend
    npm install
    ```
2.  **Uruchomienie:**
    ```bash
    npm run dev
    ```
    Aplikacja domyslnie uruchomi sie na porcie 3000.

---

## Endpointy API (Backend)

Backend wystawia nastepujace endpointy REST API:

### Modul Czatu (`/api/chat`)
*   `GET /api/chat/all` - Pobiera liste wszystkich identyfikatorow konwersacji (posortowane od najnowszych).
*   `POST /api/chat/create` - Tworzy nowa, pusta konwersacje i zwraca jej identyfikator.
*   `GET /api/chat/{chatId}/messages` - Pobiera historie wiadomosci dla konkretnej konwersacji (uwzgledniajac powiazane zrodla/obrazy).
*   `POST /api/chat/{chatId}/send` - Wysyla nowa wiadomosc do czatu i uruchamia proces LLM. Wymaga ciala zadania w postaci obiektu z polem `message`.
*   `POST /api/chat/{chatId}/rename` - Zmienia nazwe konwersacji. Wymaga ciala zadania z polem `newName`.

### Modul Folderow (`/api/folders`)
*   `GET /api/folders` - Pobiera liste wszystkich folderow.
*   `POST /api/folders/create` - Tworzy nowy folder. Wymaga ciala zadania z polem `name`.
*   `DELETE /api/folders/{id}` - Usuwa folder o wskazanym identyfikatorze.
*   `POST /api/folders/{id}/upload` - Przesyla i przetwarza (ingestion) nowy plik (`multipart/form-data` z polem `file`) przypisujac go do wybranego folderu. Obsluguje ekstrakcje tekstu, wektoryzacje oraz detekcje obrazow.

### Modul Danych i Ingestii (`/api/data`)
*   `GET /api/data/files` - Pobiera liste wszystkich plikow w systemie (zwraca m.in. sciezki, nazwy plikow oraz dane obrazow w formacie Base64).
*   `POST /api/data/files/move` - Przenosi pliki do innego folderu. Wymaga ciala zadania zawierajacego `targetFolderId` oraz liste `filePaths`. Aktualizuje rowniez metadane w bazie wektorowej.
*   `POST /api/data/files/rename` - Zmienia nazwe pliku. Wymaga ciala zadania zawierajacego `oldPath` oraz `newName`. Aktualizuje sciezke pliku w systemie oraz metadane w bazie wektorowej.
*   `DELETE /api/data/clear` - Czysci wszystkie dane z tabeli osadzen (embeddings). Uwaga: operacja nieodwracalna.