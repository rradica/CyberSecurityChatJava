package com.secassist.model;

import java.util.List;

/**
 * Einheitliches Antwortobjekt fuer alle Chat-, Evidence- und Workflow-Pfade.
 *
 * <p>Der Record buendelt den eigentlichen Antworttext mit optionalen Quellen,
 * Warnungen, Tool-Ergebnissen und einem transparenten Sicherheitskontext fuer
 * das Frontend. Dadurch kann die UI sowohl harmlose Analyseantworten als auch
 * sicherheitsrelevante Workflow-Effekte mit derselben Struktur darstellen.</p>
 *
 * <p>Diese einheitliche Form ist auch fuer den Workshop hilfreich: Teilnehmerinnen
 * und Teilnehmer sehen nicht nur, was das System antwortet, sondern auch welche
 * Quellen, Warnungen und Folgeeffekte dabei im Spiel waren. Das erleichtert die
 * Analyse von Schwachstellen und Blue-Team-Fixes erheblich.</p>
 *
 * @param reply           Antworttext aus dem Backend bzw. LLM-Service
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
    /**
     * Erstellt eine einfache Textantwort ohne Quellen, Tool-Ergebnis oder Warnungen.
     *
     * @param reply der auszugebende Antworttext
     * @return standardisierte Chat-Antwort ohne Zusatzdaten
     */
    public static ChatResponse text(String reply) {
        return new ChatResponse(reply, List.of(), null, List.of(), null);
    }

    /**
     * Erstellt eine Antwort mit Quellen, aber ohne Tool-Ergebnis und Warnungen.
     *
     * @param reply   der auszugebende Antworttext
     * @param sources die genutzten Quellen-IDs
     * @return standardisierte Chat-Antwort mit Quellenliste
     */
    public static ChatResponse withSources(String reply, List<String> sources) {
        return new ChatResponse(reply, sources, null, List.of(), null);
    }
}
