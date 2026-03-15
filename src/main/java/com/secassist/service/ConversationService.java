package com.secassist.service;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secassist.llm.LlmService;
import com.secassist.model.ChatRequest;
import com.secassist.model.ChatResponse;
import com.secassist.model.ConversationIntent;
import com.secassist.model.ConversationRequest;
import com.secassist.model.DemoCase;
import com.secassist.model.DocumentChunk;
import com.secassist.retrieval.RetrievalService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Konversationsschicht ueber dem ChatOrchestrator.
 *
 * <p>Uebersetzt natuerliche Freitext-Nachrichten in strukturierte
 * {@link ChatRequest}-Objekte. Trackt den aktiven Fall in der Session,
 * sodass Folgefragen zum selben Fall ohne erneute Fallnennung moeglich sind.</p>
 *
 * <p>Wichtig: Diese Schicht ist eine reine Uebersetzung. Sie beruehrt
 * keine der Sicherheitslogiken (Policy, Retrieval, Tool-Freigabe).
 * Alle Schwachstellen bleiben exakt wie bisher bestehen.</p>
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final Set<String> CASE_STOP_WORDS = Set.of(
            "eine", "einer", "einem", "einen", "eines", "der", "die", "das", "dem", "den",
            "und", "oder", "aber", "noch", "schon", "bitte", "habe", "haben", "hat", "mit",
            "von", "vom", "zum", "zur", "ist", "sind", "war", "were", "this", "that", "about",
            "issue", "case", "hello", "help", "mehr", "info", "mal", "show", "tell"
    );

    private static final String SESSION_ACTIVE_CASE = "activeCase";

    private final LlmService llmService;
    private final ChatOrchestrator orchestrator;
    private final DemoCaseService demoCaseService;
    private final RetrievalService retrievalService;

    public ConversationService(LlmService llmService,
                               ChatOrchestrator orchestrator,
                               DemoCaseService demoCaseService,
                               RetrievalService retrievalService) {
        this.llmService = llmService;
        this.orchestrator = orchestrator;
        this.demoCaseService = demoCaseService;
        this.retrievalService = retrievalService;
    }

    /**
     * Verarbeitet eine konversationelle Nachricht.
     *
     * @param request die Freitext-Nachricht mit Rolle
     * @param session die HTTP-Session
     * @return die Chatbot-Antwort
     */
    public ChatResponse processMessage(ConversationRequest request, HttpSession session) {
        String message = request.message();
        if (message == null || message.isBlank()) {
            return ChatResponse.text("Bitte beschreiben Sie einen Sicherheitsvorfall oder stellen Sie eine Frage.");
        }

        // Intent + Case per LLM erkennen
        List<DemoCase> cases = demoCaseService.getPublicCases();
        ConversationIntent detected = llmService.detectIntent(message, cases);
        ConversationIntent intent = normalizeIntent(message, cases, detected.sanitized());

        log.debug("Detected intent: case={}, intent={}, summary={}",
                intent.caseId(), intent.intent(), intent.summary());

        // Case aus Erkennung oder Session-Fallback
        String caseId = resolveCase(intent, session, request.caseId());

        if (caseId == null) {
            return ChatResponse.text(
                    "Ich helfe Ihnen gerne bei einem Sicherheitsvorfall. "
                    + "K\u00f6nnten Sie die Situation genauer beschreiben? Zum Beispiel:\n\n"
                    + "- \"Ich habe eine verd\u00e4chtige Rechnung von einem Lieferanten erhalten\"\n"
                    + "- \"Jemand will sein VPN-Passwort von einem unbekannten Standort zur\u00fccksetzen\"\n"
                    + "- \"Wir haben eine Phishing-Mail an die Finanzabteilung entdeckt\"");
        }

        // Aktiven Case in Session speichern
        session.setAttribute(SESSION_ACTIVE_CASE, caseId);

        // Notiz direkt verarbeiten – ohne Orchestrator
        if ("add_note".equals(intent.intent())) {
            return handleAddNote(caseId, message);
        }

        // Intent auf ChatRequest-Action mappen
        ChatRequest chatRequest = mapToRequest(request.role(), caseId, intent.intent(), message);

        log.debug("Mapped to ChatRequest: role={}, caseId={}, action={}, message={}",
                chatRequest.role(), chatRequest.caseId(), chatRequest.action(),
                truncate(chatRequest.message(), 80));

        return orchestrator.processRequest(chatRequest, session);
    }

    /**
     * Ermittelt die aktive Case-ID: Erkennung hat Vorrang, dann Session-Fallback.
     */
    private String resolveCase(ConversationIntent intent, HttpSession session, String explicitCaseId) {
        if (explicitCaseId != null && !explicitCaseId.isBlank()) {
            return explicitCaseId;
        }
        if (intent.hasCaseId()) {
            return intent.caseId();
        }
        return (String) session.getAttribute(SESSION_ACTIVE_CASE);
    }

    /**
     * Mappt den erkannten Intent auf einen ChatRequest.
     */
    private ChatRequest mapToRequest(String role, String caseId, String intent, String message) {
        return switch (intent) {
            case "triage"        -> new ChatRequest(role, caseId, message, "workflow");
            case "handover"      -> new ChatRequest(role, caseId, message, "handover");
            case "similar_cases" -> new ChatRequest(role, caseId, message, "similar_cases");
            case "evidence"      -> new ChatRequest(role, caseId, "", "evidence");
            default              -> new ChatRequest(role, caseId, message, "chat");
        };
    }

    /**
     * Stabilisiert die LLM-Intent-Erkennung fuer die vorbereiteten Workshop-Faelle.
     *
     * <p>Das LLM bleibt in der Kette, aber explizite, gut erkennbare Nutzerwuensche
     * werden deterministisch normalisiert. So bleiben die Demo-Cases im Workshop
     * reproduzierbar, ohne die Real-LLM-Nutzung auszuschalten.</p>
     */
    private ConversationIntent normalizeIntent(String message,
                                               List<DemoCase> availableCases,
                                               ConversationIntent detected) {
        String lower = message.toLowerCase();

        String caseId = detected.caseId();
        if (caseId == null || caseId.isBlank()) {
            caseId = inferCaseId(lower, availableCases);
        }

        String intent = detected.intent();
        if (containsAny(lower, "notiz:", "vermerke", "zusatzinfo", "bitte notieren")) {
            intent = "add_note";
        } else if (containsAny(lower, "aehnliche faelle", "aehnliche vorfaelle", "in der vergangenheit", "fruehere vorfaelle", "similar")) {
            intent = "similar_cases";
        } else if (containsAny(lower, "uebergabe", "handover")) {
            intent = "handover";
        } else if (containsAny(lower, "beweise", "quellen", "evidence")) {
            intent = "evidence";
        } else if (containsAny(lower, "bewerte das risiko", "bewerte", "triage", "risiko")) {
            intent = "triage";
        }

        return new ConversationIntent(caseId, intent, detected.summary()).sanitized();
    }

    /**
     * Leitet die plausibelste Fall-ID aus Nachricht und Demo-Fallkatalog ab.
     *
     * <p>Die Heuristik ist bewusst generisch gehalten: Es werden Begriffe aus
     * Titel, Beschreibung, Typ und verknuepften Dokumenten der verfuegbaren Faelle
     * gegen die Nutzernachricht gematcht. So wirkt die Fallback-Erkennung eher
     * wie ein einfacher Produktmechanismus als wie ein hart codierter Demo-Schalter.</p>
     */
    private String inferCaseId(String normalizedMessage, List<DemoCase> availableCases) {
        List<String> messageTokens = extractMeaningfulTokens(normalizedMessage);
        int bestScore = 0;
        String bestCaseId = null;

        for (DemoCase demoCase : availableCases) {
            String searchable = (demoCase.title() + " "
                    + demoCase.description() + " "
                    + demoCase.type() + " "
                    + String.join(" ", demoCase.relatedDocIds())).toLowerCase();

            int score = 0;
            for (String token : messageTokens) {
                if (searchable.contains(token)) {
                    score++;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestCaseId = demoCase.id();
            }
        }

        return bestScore > 0 ? bestCaseId : null;
    }

    private List<String> extractMeaningfulTokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (String token : text.toLowerCase().split("[^\\p{L}\\p{N}._-]+")) {
            if (token.length() < 4 || CASE_STOP_WORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verarbeitet eine Notiz-Nachricht: speichert den Text als User-Note
     * ueber den RetrievalService und gibt eine Bestaetigung zurueck.
     */
    private ChatResponse handleAddNote(String caseId, String noteText) {
        DocumentChunk note = retrievalService.addUserNote(caseId, noteText);
        log.debug("Note added via chat flow: id={}, caseId={}", note.id(), caseId);

        String trustLabel = translateTrust(note.trustLevel());
        String classLabel = translateClassification(note.classification());

        String reply = "Notiz zu **" + caseId + "** gespeichert."
                + "\n**Vertrauensstufe:** " + trustLabel
                + " | **Klassifizierung:** " + classLabel;

        List<String> warnings = new ArrayList<>();
        if ("high".equals(note.trustLevel())
                && ("case_note".equals(note.sourceType()) || "user_note".equals(note.sourceType()))) {
            warnings.add("\u26A0 Benutzernotiz als vertrauensw\u00fcrdige interne Quelle gespeichert");
        }

        return new ChatResponse(reply, List.of(), null, warnings, null);
    }

    private static String translateTrust(String level) {
        return switch (level) {
            case "high"      -> "Vertrauensw\u00fcrdig";
            case "medium"    -> "Mittel";
            case "low"       -> "Niedrig";
            case "untrusted" -> "Nicht vertrauensw\u00fcrdig";
            default          -> level;
        };
    }

    private static String translateClassification(String classification) {
        return switch (classification) {
            case "public"       -> "\u00d6ffentlich";
            case "internal"     -> "Intern";
            case "confidential" -> "Vertraulich";
            default             -> classification;
        };
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
