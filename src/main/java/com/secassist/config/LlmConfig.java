package com.secassist.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.secassist.llm.LlmService;
import com.secassist.llm.OpenAiChatService;

/**
 * Spring-Konfiguration fuer die LLM-Anbindung von SecAssist.
 *
 * <p>Die Klasse registriert die konkrete {@link LlmService}-Implementierung,
 * die von den Orchestrierungs- und Konversationsdiensten verwendet wird. Im
 * aktuellen Projektstand ist ausschliesslich der echte
 * {@link OpenAiChatService} verdrahtet. Damit bleibt die LLM-Integration im
 * Code leicht nachvollziehbar und ohne versteckte Provider-Auswahl.</p>
 *
 * <p>Die Konfiguration ist bewusst klein gehalten: Sie kapselt nur die
 * Instanziierung des Services und ueberlaesst Prompting, Strukturierung und
 * Sicherheitsgrenzen den dafuer vorgesehenen Fachklassen. Fuer alle
 * LLM-gestuetzten Pfade ist daher ein gueltiger {@code OPENAI_API_KEY}
 * erforderlich.</p>
 */
@Configuration
public class LlmConfig {

    /**
     * Registriert den produktiv genutzten {@link LlmService} auf Basis von Spring AI.
     *
     * @param chatClientBuilder Builder fuer den Spring-AI-ChatClient
     * @return OpenAI-basierte Implementierung des LLM-Service
     */
    @Bean
    public LlmService openAiChatService(ChatClient.Builder chatClientBuilder) {
        return new OpenAiChatService(chatClientBuilder.build());
    }
}
