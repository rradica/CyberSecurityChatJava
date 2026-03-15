package com.secassist.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.secassist.llm.LlmService;
import com.secassist.llm.OpenAiChatService;

/**
 * Konfiguration für den LLM-Service.
 *
 * <p>Verwendet Spring AI's {@link ChatClient} für echte OpenAI-Aufrufe.
 * Erfordert einen gültigen {@code OPENAI_API_KEY}.</p>
 */
@Configuration
public class LlmConfig {

    @Bean
    public LlmService openAiChatService(ChatClient.Builder chatClientBuilder) {
        return new OpenAiChatService(chatClientBuilder.build());
    }
}
