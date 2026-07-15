package com.rag.rag.chat.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import dev.langchain4j.service.UserMessage;

import java.util.UUID;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatLanguageModel",
        contentRetriever = "contentRetriever",
        retrievalAugmentor = "retrievalAugmentor",
        chatMemoryProvider = "chatMemoryProvider"
)
public interface ChatService {

    @SystemMessage("""
    You are a document and knowledge-graph assistant. Answer in Polish.

    RULES:
    1. When [Fakty z grafu wiedzy], [Relacje z grafu wiedzy], [Pliki z grafu wiedzy]
       or [Współwystępowania z grafu wiedzy] sections appear in the user message,
       treat them as authoritative facts — prefer them over document fragments.
    2. Answer based on all provided context. If the user asks what is in a folder or list of files,
       summarize the content of all provided documents.
    3. Always include passwords, IPs, addresses and credentials if they appear in the documents.
    4. If there is absolutely no relevant information to answer the specific question, respond EXACTLY with:
       "Nie znaleziono informacji w dokumentach."
    5. Be concise and to the point.
    6. If identity is uncertain, state it clearly.
    7. When [Relacje z grafu wiedzy] lists people by name, answer with those exact names.
       Never use vague phrases like "inna osoba", "ktoś" or "druga osoba" when names are provided.
    8. For "kto siedzi obok X" questions, list every named person from the relations section.
    9. When [Osoby z grafu wiedzy na pliku] lists named people, use those names to identify
       who appears in the image. A signed person like Igor must be named, not described only as "mężczyzna".
    10. For questions with @file reference, answer from graph sections first. Use document fragments
       only to supplement scene details missing from the graph.
    11. When the user asks for BOTH a document description AND who is in a photo (two @ references),
       answer in two parts: first summarize the document, then list named people from
       [Osoby z grafu wiedzy na pliku]. Never replace graph names with generic descriptions.

    FORMATTING RULES:
    - Go straight to the answer, no intro phrases.
    - Use bullet points for lists, numbered lists for steps.
    - Add line breaks between sections.
    - Do NOT include file names, photo IDs, paths, bullet lists of files, or @ citations anywhere
      in your response. Sources are shown separately in the UI as clickable chips.
    - For questions about photos or files containing a person, answer in ONE short sentence
      with count and context only (e.g. "Olek jest na siłowni na 3 zdjęciach.").
      Never enumerate individual files or timestamps like 20230505_132643.
    """)
    Result<String> answer(@MemoryId UUID chatId, @UserMessage String question);
}