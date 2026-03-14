package com.secassist.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM-Service mit echtem OpenAI-API-Aufruf über Spring AI.
 *
 * <p>Wird nur aktiv, wenn {@code secassist.mock-llm=false} und ein
 * gültiger {@code OPENAI_API_KEY} konfiguriert ist.</p>
 */
public class OpenAiChatService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatService.class);

    private final ChatClient chatClient;

    public OpenAiChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
        log.info("OpenAI ChatService initialized – using real LLM");
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        log.debug("Calling OpenAI with system prompt length={}, user message length={}",
                systemPrompt.length(), userMessage.length());

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();
            return response != null ? response : "No response from model.";
        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            return "Error communicating with the LLM: " + e.getMessage()
                    + "\n\nFallback: Please check your API key or switch to mock mode "
                    + "(set SECASSIST_MOCK_LLM=true).";
        }
    }
}
