# RAG (Retrieval-Augmented Generation) - Document Management System

This project is a RAG (Retrieval-Augmented Generation) application designed for document management, analysis, and interaction through a chat interface powered by language models. The system supports text documents, PDF files, and images.

## Architecture and Technology Stack

The project consists of two main parts: the backend (Spring Boot) and the frontend (Next.js).

### Backend
*   **Language:** Java 17
*   **Framework:** Spring Boot 4.0.2
*   **Database:** PostgreSQL with PGVector extension (for vector storage)
*   **AI/LLM:** LangChain4j, Ollama, OpenAI API (compatible with Groq)
*   **Models:** 
    *   Llama 3.1 (chat)
    *   MiniCPM-V (vision)
    *   bge-m3 (embeddings)
*   **Document Processing:** Apache PDFBox, Apache Tika

### Frontend
*   **Framework:** Next.js 16.2.2, React 19
*   **Styling:** TailwindCSS 4
*   **Main Features:**
    *   A sliding side panel (dashboard) containing a list of conversations and navigation.
    *   A chat interface for conversing with the assistant about uploaded documents.
    *   Folder management and file preview (with support for image thumbnails and icons for text/PDF files).

---

## Configuration and Setup

### Prerequisites
*   Docker and Docker Compose
*   Java 17 (for the backend)
*   Maven
*   Node.js 20+ (for the frontend)
*   Ollama (installed locally or via container)

### Step 1: Database Setup
The main project directory contains a `docker-compose.yml` file which defines a PostgreSQL database container with the PGVector extension.
Start the database using the following command:
```bash
docker-compose up -d pgvector
```

### Step 2: Ollama Models Setup (If using local LLM)
If you choose to run the language models locally via Ollama, you must pull the required models before starting the application. Open your terminal and run the following commands:
```bash
ollama pull llama3.1
ollama pull minicpm-v
ollama pull bge-m3
```

### Step 3: Backend Configuration
The server source code is located in the `backend/` directory.

1.  **Environment Variables:**
    The backend can be configured using a `backend/.env` file (optional) or directly within `backend/src/main/resources/application.properties`. 

    **For OpenAI / Groq:**
    ```properties
    llm.provider=openai
    OPENAI_API_KEY=your_api_key
    ```

    **For Ollama (Local):**
    ```properties
    llm.provider=ollama
    ollama.base.url=http://localhost:11434
    ```

    **Database Settings** (Adjust if changed in the docker-compose setup):
    ```properties
    DB_URL=jdbc:postgresql://localhost:5433/vector_db
    DB_USERNAME=user
    DB_PASSWORD=password
    ```
3.  **Running the Server:**
    ```bash
    cd backend
    ./mvnw spring-boot:run
    ```

### Step 4: Frontend Configuration
The client application code is located in the `frontend/` directory.

1.  **Installing Dependencies:**
    ```bash
    cd frontend
    npm install
    ```
2.  **Running the Application:**
    ```bash
    npm run dev
    ```
    The application will run on port 3000 by default.

---

## API Endpoints (Backend)

The backend exposes the following REST API endpoints:

### Chat Module (`/api/chat`)

*   **`GET /api/chat/all`**
    *   **Description:** Retrieves a list of all conversation IDs (sorted from newest to oldest).

*   **`POST /api/chat/create`**
    *   **Description:** Creates a new, empty conversation and returns its identifier.

*   **`GET /api/chat/{chatId}/messages`**
    *   **Description:** Retrieves the message history for a specific conversation (including associated sources/images).

*   **`POST /api/chat/{chatId}/send`**
    *   **Description:** Sends a new message to the chat and triggers the LLM process.
    *   **Request Body (JSON):**
        ```json
        {
          "message": "Hello, what's in this document?"
        }
        ```

*   **`POST /api/chat/{chatId}/rename`**
    *   **Description:** Renames the conversation.
    *   **Request Body (JSON):**
        ```json
        {
          "newName": "New Conversation Name"
        }
        ```

### Folder Module (`/api/folders`)

*   **`GET /api/folders`**
    *   **Description:** Retrieves a list of all folders.

*   **`POST /api/folders/create`**
    *   **Description:** Creates a new folder.
    *   **Request Body (JSON):**
        ```json
        {
          "name": "Documents"
        }
        ```

*   **`DELETE /api/folders/{id}`**
    *   **Description:** Deletes the folder with the specified identifier.

*   **`POST /api/folders/{id}/upload`**
    *   **Description:** Uploads and processes (ingestion) a new file, assigning it to the selected folder. Supports text extraction, vectorization, and image analysis.
    *   **Request Body:** `multipart/form-data` containing a `file` field.

### Data and Ingestion Module (`/api/data`)

*   **`GET /api/data/files`**
    *   **Description:** Retrieves a list of all files in the system (returns paths, filenames, and image data in Base64 format among other things).

*   **`POST /api/data/files/move`**
    *   **Description:** Moves files to a different folder. Also updates metadata in the vector database.
    *   **Request Body (JSON):**
        ```json
        {
          "targetFolderId": "uuid-of-the-target-folder",
          "filePaths": [
            "dir://old-folder/file1.png",
            "dir://old-folder/file2.txt"
          ]
        }
        ```

*   **`POST /api/data/files/rename`**
    *   **Description:** Renames a file. Updates the file path in the system and the metadata in the vector database.
    *   **Request Body (JSON):**
        ```json
        {
          "oldPath": "dir://folder/old-name.txt",
          "newName": "new-name.txt"
        }
        ```

*   **`DELETE /api/data/clear`**
    *   **Description:** Clears all data from the embeddings table. **Warning:** This operation is irreversible.
