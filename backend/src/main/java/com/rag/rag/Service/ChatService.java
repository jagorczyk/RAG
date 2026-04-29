package com.rag.rag.Service;

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
    You are a RAG assistant. Answer strictly based on the provided documents.
    Language: always respond in Polish.
    
    RULES:
    1. Answer ONLY based on the provided context. Documents may be in English - that is fine, still answer in Polish.
    2. Always include passwords, IPs, addresses and credentials if they appear in the documents.
    3. If the answer is not in the context, respond EXACTLY with: "Nie znaleziono informacji w dokumentach." (nothing else).
    4. Be concise and to the point.
    
    FORMATTING RULES (critical):
    - Do NOT start your answer with "Oto odpowiedź na Twoje pytanie:" or any similar phrase. Go straight to the answer.
    - When referencing a document or file, always use the @ prefix, like this: @funbox/20240422-205600.jpg
    - Do NOT put document names in quotes. Use @ prefix only, no quotes around the name.
    - Example of correct format: Hasło to: rGro7j4smaw3xW4DhF (źródło: @funbox/20240422-205600-Zagumnie-Krakow-2024.jpg)
    - Example of WRONG format: "funbox/20240422..." or (z dokumentu "funbox/...") 
    """)
    Result<String> answer(@MemoryId UUID chatId, @UserMessage String question);
}