package com.secassist.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.secassist.llm.LlmService;
import com.secassist.llm.OpenAiChatService;

/**
 * Konfiguration fuer den LLM-Service.
 *
 * <p>Verwendet Spring AI's {@link ChatClient} fuer echte OpenAI-Aufrufe.
 * Erfordert einen gueltigen {@code OPENAI_API_KEY}.</p>
 */
@Configuration
public class LlmConfig {

    @Bean
    public LlmService openAiChatService(ChatClient.Builder chatClientBuilder) {
        return new OpenAiChatService(chatClientBuilder.build());
    }
}
