package com.secassist.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.secassist.llm.LlmService;
import com.secassist.llm.MockLlmService;
import com.secassist.llm.OpenAiChatService;

/**
 * Konfiguration für den LLM-Service.
 *
 * <p>Im Mock-Modus (default) wird {@link MockLlmService} verwendet,
 * sodass die App auch ohne API-Key vollständig funktioniert.
 * Wenn {@code secassist.mock-llm=false}, wird Spring AI's ChatClient
 * für echte OpenAI-Aufrufe genutzt.</p>
 */
@Configuration
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "secassist.mock-llm", havingValue = "false")
    public LlmService openAiChatService(ChatClient.Builder chatClientBuilder) {
        return new OpenAiChatService(chatClientBuilder.build());
    }

    @Bean
    @ConditionalOnProperty(name = "secassist.mock-llm", havingValue = "true", matchIfMissing = true)
    public LlmService mockLlmService() {
        return new MockLlmService();
    }
}
