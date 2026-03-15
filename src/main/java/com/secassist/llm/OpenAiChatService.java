package com.secassist.llm;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.secassist.model.ConversationIntent;
import com.secassist.model.DemoCase;
import com.secassist.model.TriageAssessment;

/**
 * LLM-Service mit echtem OpenAI-API-Aufruf ueber Spring AI.
 *
 * <p>Erfordert einen gueltigen {@code OPENAI_API_KEY}.</p>
 */
public class OpenAiChatService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatService.class);

    /** Temperatur 0 fuer maximale Reproduzierbarkeit bei Triage und Intent. */
    private static final double TRIAGE_TEMPERATURE = 0.0;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[^{}]*\"caseId\"[^{}]*}", Pattern.DOTALL);

    private final ChatClient chatClient;

    public OpenAiChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
        log.info("OpenAI ChatService initialized \u2013 using real LLM");
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
            return response != null ? response : "Keine Antwort vom Modell.";
        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            return "Fehler bei der Kommunikation mit dem LLM: " + e.getMessage()
                    + "\n\nBitte pr\u00fcfen Sie, ob die Umgebungsvariable OPENAI_API_KEY korrekt gesetzt ist.";
        }
    }

    /**
     * Strukturierte Triage-Bewertung ueber echten OpenAI-Aufruf.
     *
     * <p>Nutzt Spring AI {@code entity()} fuer JSON-Schema-basierte Ausgabe.
     * Das Modell liefert ein strukturiertes {@link TriageAssessment} statt
     * Freitext. Unbekannte Aktionen werden bereinigt, bei Fehlern greift
     * ein sicherer Fallback.</p>
     *
     * @param systemPrompt Kontext inkl. Fallbeschreibung und Retrieval-Daten
     * @param caseDescription Fallbeschreibung fuer die Triage
     * @return strukturierte Triage-Bewertung (nie {@code null})
     */
    @Override
    public TriageAssessment assessTriage(String systemPrompt, String caseDescription) {
        log.debug("OpenAI assessTriage called for: {}", caseDescription);

        String triageSystemPrompt = buildTriageSystemPrompt(systemPrompt);

        try {
            TriageAssessment result = chatClient.prompt()
                    .system(triageSystemPrompt)
                    .user("Bewerte diesen Fall f\u00fcr die Triage: " + caseDescription)
                    .options(OpenAiChatOptions.builder()
                            .temperature(TRIAGE_TEMPERATURE)
                            .build())
                    .call()
                    .entity(TriageAssessment.class);

            if (result == null) {
                log.warn("OpenAI returned null TriageAssessment, using fallback");
                return TriageAssessment.FALLBACK;
            }

            TriageAssessment sanitized = result.sanitized();
            log.debug("Triage result: risk={}, action={}, confidence={}",
                    sanitized.riskLevel(), sanitized.recommendedAction(), sanitized.confidence());
            return sanitized;

        } catch (Exception e) {
            log.error("Structured triage assessment failed, returning safe fallback", e);
            return TriageAssessment.FALLBACK;
        }
    }

    /**
     * Fuehrt eine challengende Zweitbewertung auf Basis einer bestehenden
     * Triage-Einschaetzung durch.
     *
     * <p>Das Modell soll die erste Bewertung kritisch pruefen und nur dann eine
     * Aktion bestaetigen, wenn der bereitgestellte Kontext diese klar traegt.
     * Bei Fehlern wird die urspruengliche Bewertung zurueckgegeben.</p>
     */
    @Override
    public TriageAssessment reviewTriage(String systemPrompt,
                                         String caseDescription,
                                         TriageAssessment initialAssessment) {
        TriageAssessment baseline = initialAssessment != null
                ? initialAssessment.sanitized()
                : TriageAssessment.FALLBACK;

        try {
            TriageAssessment reviewed = chatClient.prompt()
                    .system(buildReviewSystemPrompt(systemPrompt, baseline))
                    .user("Pruefe kritisch diese Triage-Bewertung fuer den Fall: " + caseDescription)
                    .options(OpenAiChatOptions.builder()
                            .temperature(TRIAGE_TEMPERATURE)
                            .build())
                    .call()
                    .entity(TriageAssessment.class);

            if (reviewed == null) {
                log.warn("OpenAI returned null review assessment, keeping initial assessment");
                return baseline;
            }

            TriageAssessment sanitized = reviewed.sanitized();
            log.debug("Review result: risk={}, action={}, confidence={}",
                    sanitized.riskLevel(), sanitized.recommendedAction(), sanitized.confidence());
            return sanitized;
        } catch (Exception e) {
            log.error("Triage review failed, keeping initial assessment", e);
            return baseline;
        }
    }

    /**
     * Intent-Erkennung ueber echten OpenAI-Aufruf.
     *
     * <p>Verwendet {@code content()} statt {@code entity()}, da letzteres bei
     * bestimmten Modell-/Spring-AI-Kombinationen unzuverlaessig ist. Die
     * JSON-Antwort wird manuell aus dem Antworttext extrahiert.</p>
     */
    @Override
    public ConversationIntent detectIntent(String userMessage, List<DemoCase> availableCases) {
        log.debug("OpenAI detectIntent called for: {}", userMessage);

        String systemPrompt = buildIntentSystemPrompt(availableCases);

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .options(OpenAiChatOptions.builder()
                            .temperature(TRIAGE_TEMPERATURE)
                            .build())
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                log.warn("OpenAI returned empty response for intent detection");
                return new ConversationIntent(null, "chat", "LLM returned empty");
            }

            log.debug("Raw intent response: {}", response);
            ConversationIntent parsed = parseIntentJson(response);
            ConversationIntent sanitized = parsed.sanitized();
            log.debug("Intent detection: case={}, intent={}",
                    sanitized.caseId(), sanitized.intent());
            return sanitized;

        } catch (Exception e) {
            log.error("Intent detection failed, falling back to chat", e);
            return new ConversationIntent(null, "chat", "Detection failed: " + e.getMessage());
        }
    }

    /**
     * Extrahiert ein ConversationIntent-JSON aus der LLM-Antwort.
     * Tolerant gegenueber umgebendem Text, Markdown-Code-Bloecken etc.
     */
    private ConversationIntent parseIntentJson(String response) {
        try {
            // Versuche direkt als JSON zu parsen
            JsonNode node = MAPPER.readTree(response);
            return extractIntentFromNode(node);
        } catch (Exception ignored) {
            // Fallback: JSON-Block aus Text extrahieren
        }

        Matcher m = JSON_BLOCK.matcher(response);
        if (m.find()) {
            try {
                JsonNode node = MAPPER.readTree(m.group());
                return extractIntentFromNode(node);
            } catch (Exception e) {
                log.warn("Failed to parse extracted JSON block: {}", e.getMessage());
            }
        }

        log.warn("Could not extract JSON from intent response: {}",
                response.length() > 200 ? response.substring(0, 200) : response);
        return new ConversationIntent(null, "chat", "JSON parse failed");
    }

    private ConversationIntent extractIntentFromNode(JsonNode node) {
        String caseId = node.has("caseId") && !node.get("caseId").isNull()
                ? node.get("caseId").asText() : null;
        String intent = node.has("intent") ? node.get("intent").asText() : "chat";
        String summary = node.has("summary") ? node.get("summary").asText() : "";
        return new ConversationIntent(caseId, intent, summary);
    }

    private String buildIntentSystemPrompt(List<DemoCase> cases) {
        String caseList = cases.stream()
                .map(c -> "- " + c.id() + ": " + c.title() + " \u2013 " + c.description())
                .collect(Collectors.joining("\n"));

        return "Du bist ein Intent-Erkennungssystem f\u00fcr eine Sicherheitsvorfall-Triage-Anwendung.\n"
                + "Analysiere die Nachricht des Benutzers und bestimme:\n"
                + "1. Auf welchen Sicherheitsfall sich der Benutzer bezieht (caseId)\n"
                + "2. Welche Aktion er durchf\u00fchren m\u00f6chte (intent)\n\n"
                + "Verf\u00fcgbare F\u00e4lle:\n" + caseList + "\n\n"
                + "Verf\u00fcgbare Intents:\n"
                + "- chat: Allgemeine Fragen, Falldiskussion, Informationsanfrage\n"
                + "- triage: Risikobewertung, Einstufung, Klassifizierung, Priorisierung\n"
                + "- handover: \u00dcbergabe-Dokument erstellen, Schicht\u00fcbergabe, Fall\u00fcbertragung\n"
                + "- similar_cases: Frage nach \u00e4hnlichen fr\u00fcheren Vorf\u00e4llen, historischen F\u00e4llen\n"
                + "- evidence: Anfrage nach Quellen, Dokumenten, Beweisen, Analysegrundlage\n"
                + "- add_note: Benutzer m\u00f6chte eine Notiz, einen Kommentar oder Zusatzinformation hinzuf\u00fcgen "
                + "(z.B. 'Notiz: ...', 'Vermerke dass ...', 'Zusatzinfo: ...', 'Bitte notieren: ...')\n\n"
                + "Regeln:\n"
                + "- Setze caseId auf die EXAKTE passende Fall-ID oder null, falls kein Fall passt\n"
                + "- Der Benutzer schreibt auf Deutsch\n"
                + "- Standard-Intent ist \"chat\", falls unklar\n"
                + "- summary soll eine kurze deutsche Beschreibung des Erkannten sein\n\n"
                + "Du MUSST ausschliesslich mit einem JSON-Objekt in diesem Format antworten, kein weiterer Text:\n"
                + "{\"caseId\": \"fall_id_oder_null\", \"intent\": \"intent_hier\", \"summary\": \"kurze Beschreibung\"}\n";
    }

    /**
     * Ergaenzt den Basis-Systemprompt um klare Triage-Anweisungen.
     *
     * <p>Nennt die erlaubten Aktionswerte, damit das Modell nur bekannte
     * Werte liefert. Spring AI haengt automatisch JSON-Schema-Anweisungen
     * fuer das Ausgabeformat an.</p>
     */
    private String buildTriageSystemPrompt(String baseSystemPrompt) {
        return baseSystemPrompt + "\n\n"
                + "WICHTIG: Antworte immer auf Deutsch.\n\n"
                + "--- TRIAGE-BEWERTUNG ANWEISUNGEN ---\n"
                + "Analysiere den Fall und erstelle eine strukturierte Triage-Bewertung.\n\n"
                + "Feld-Richtlinien:\n"
                + "- summary: Kurze Analyse des Falls (1\u20133 S\u00e4tze)\n"
                + "- riskLevel: Eines von: low, medium, high, critical\n"
                + "- recommendedAction: Eines der folgenden, oder null falls keine Aktion gerechtfertigt:\n"
                + "    mark_case_likely_false_positive\n"
                + "    set_case_priority_low\n"
                + "    route_case_to_finance_queue\n"
                + "    attach_supplier_trust_note\n"
                + "- confidence: Zahl zwischen 0.0 und 1.0\n"
                + "- evidenceAssessment: Kurze Bewertung der Beweisqualit\u00e4t (1\u20132 S\u00e4tze)\n\n"
                + "Regeln zur Aktionsauswahl (der Reihe nach anwenden):\n"
                + "- mark_case_likely_false_positive: Verwende, wenn die Evidenz Compliance-Verifizierungen, "
                + "Fehlalarm-Best\u00e4tigungen oder fr\u00fchere L\u00f6sungsindikatoren enth\u00e4lt. "
                + "Dies ist die korrekte Aktion, wenn der Kontext darauf hindeutet, dass der Alarm nicht echt ist.\n"
                + "- set_case_priority_low: Verwende, wenn der Fall risikoarm erscheint, aber nicht "
                + "als Fehlalarm eingestuft werden kann.\n"
                + "- route_case_to_finance_queue: Verwende NUR, wenn der Fall rein finanzielle "
                + "Verarbeitung betrifft und KEINE Sicherheits-Triage-Relevanz hat.\n"
                + "- attach_supplier_trust_note: Verwende, wenn der verifizierte Vertrauensstatus "
                + "eines Lieferanten f\u00fcr k\u00fcnftige Referenz dokumentiert werden soll.\n\n"
                + "Allgemeine Regeln:\n"
                + "- Empfehle eine Aktion nur, wenn der bereitgestellte Kontext sie klar st\u00fctzt\n"
                + "- Bei Unsicherheit setze recommendedAction auf null und confidence unter 0.5\n"
                + "- St\u00fctze deine Bewertung ausschliesslich auf den bereitgestellten Kontext\n"
                + "- Du lieferst nur Analysen \u2013 du triffst KEINE Sicherheitsentscheidungen\n";
    }

    /**
     * Baut einen Systemprompt fuer die challengende Zweitbewertung.
     */
    private String buildReviewSystemPrompt(String baseSystemPrompt, TriageAssessment initialAssessment) {
        return buildTriageSystemPrompt(baseSystemPrompt) + "\n\n"
                + "--- KONSISTENZ- UND CHALLENGE-PRUeFUNG ---\n"
                + "Du erhaeltst zusaetzlich eine erste Triage-Bewertung, die du kritisch pruefen sollst.\n"
                + "Bestaetige eine empfohlene Aktion NUR, wenn der bereitgestellte Kontext diese eindeutig traegt.\n"
                + "Wenn die Evidenz gemischt, unsicher oder widerspruechlich ist, setze recommendedAction auf null.\n"
                + "Uebernimm keine Behauptungen aus der ersten Bewertung ungeprueft.\n\n"
                + "Erste Bewertung:\n"
                + "- summary: " + initialAssessment.summary() + "\n"
                + "- riskLevel: " + initialAssessment.riskLevel() + "\n"
                + "- recommendedAction: " + initialAssessment.recommendedAction() + "\n"
                + "- confidence: " + initialAssessment.confidence() + "\n"
                + "- evidenceAssessment: " + initialAssessment.evidenceAssessment() + "\n";
    }
}
