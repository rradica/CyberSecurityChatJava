package com.secassist.model;

import java.util.List;

/**
 * Antwort des Chatbots an das Frontend.
 *
 * @param reply           Antworttext (vom LLM oder Mock)
 * @param sources         Liste der genutzten Quellen-IDs
 * @param toolResult      Ergebnis einer Workflow-/Tool-Aktion (optional)
 * @param warnings        Warnungen oder Hinweise fuer das UI
 * @param securityContext Verarbeitungskontext mit Rolle, Aktion und Quell-Metadaten (optional)
 */
public record ChatResponse(
        String reply,
        List<String> sources,
        ToolActionResult toolResult,
        List<String> warnings,
        SecurityContext securityContext
) {
    /** Einfache Textantwort ohne Quellen, Tool-Ergebnis oder Warnungen. */
    public static ChatResponse text(String reply) {
        return new ChatResponse(reply, List.of(), null, List.of(), null);
    }

    /** Antwort mit Quellen. */
    public static ChatResponse withSources(String reply, List<String> sources) {
        return new ChatResponse(reply, sources, null, List.of(), null);
    }
}
