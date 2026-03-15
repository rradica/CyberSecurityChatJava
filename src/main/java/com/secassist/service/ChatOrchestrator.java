package com.secassist.service;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secassist.llm.LlmService;
import com.secassist.model.CaseBriefing;
import com.secassist.model.CaseState;
import com.secassist.model.ChatRequest;
import com.secassist.model.ChatResponse;
import com.secassist.model.DemoCase;
import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;
import com.secassist.model.SecurityContext;
import com.secassist.model.SourceMeta;
import com.secassist.model.ToolActionResult;
import com.secassist.model.TriageAssessment;
import com.secassist.policy.PolicyEngine;
import com.secassist.retrieval.RetrievalService;
import com.secassist.tools.IncidentWorkflowService;
import com.secassist.tools.ToolPolicyDecision;
import com.secassist.tools.ToolPolicyService;

/**
 * Orchestriert den gesamten Request-Ablauf gemaeß „Berechtigung vor Kontext".
 *
 * <p>Ablauf:
 * <ol>
 *   <li>Actor bestimmen (Rolle)</li>
 *   <li>Zweck / Aktion bestimmen</li>
 *   <li>Erlaubte Quellen bestimmen (Policy)</li>
 *   <li>Kontext aufbauen (Retrieval)</li>
 *   <li>Text generieren oder Tool-Vorschlag erstellen</li>
 * </ol></p>
 */
@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private static final String SESSION_ROLE = "currentRole";
    private static final String SESSION_HISTORY = "conversationHistory";
    private static final String SESSION_LAST_CONTEXT = "lastRetrievalContext";
    private static final String SESSION_LAST_CASE_ID = "lastCaseId";

    private final PolicyEngine policyEngine;
    private final RetrievalService retrievalService;
    private final LlmService llmService;
    private final ToolPolicyService toolPolicyService;
    private final IncidentWorkflowService workflowService;
    private final DemoCaseService demoCaseService;
    private final PromptBuilder promptBuilder;

    public ChatOrchestrator(PolicyEngine policyEngine,
                            RetrievalService retrievalService,
                            LlmService llmService,
                            ToolPolicyService toolPolicyService,
                            IncidentWorkflowService workflowService,
                            DemoCaseService demoCaseService,
                            PromptBuilder promptBuilder) {
        this.policyEngine = policyEngine;
        this.retrievalService = retrievalService;
        this.llmService = llmService;
        this.toolPolicyService = toolPolicyService;
        this.workflowService = workflowService;
        this.demoCaseService = demoCaseService;
        this.promptBuilder = promptBuilder;
    }

    /**
     * Verarbeitet einen eingehenden Request.
     *
     * @param request der Chat-/Aktions-Request
     * @param session die HTTP-Session
     * @return die Antwort
     */
    public ChatResponse processRequest(ChatRequest request, HttpSession session) {
        // Schritt 1: Actor bestimmen
        Role role = Role.fromString(request.role());
        handleRoleChange(session, role);

        if (role == Role.CONTRACTOR) {
            return ChatResponse.text("Externe Partner koennen in SecAssist nur Partner-Updates zu freigegebenen Faellen senden.");
        }

        // Schritt 2: Aktion bestimmen
        String action = request.action() != null ? request.action() : "chat";
        String caseId = request.caseId();
        String message = request.message() != null ? request.message() : "";

        // Fallinfo laden
        DemoCase demoCase = demoCaseService.findById(caseId);
        CaseBriefing briefing = demoCaseService.getCaseBriefing(caseId);
        String caseDesc = describeCase(demoCase, briefing, caseId);

        return switch (action) {
            case "chat"          -> handleChat(role, caseId, caseDesc, briefing, message, session);
            case "handover"      -> handleHandover(role, caseId, caseDesc, briefing, message, session);
            case "similar_cases" -> handleSimilarCases(role, caseId, message);
            case "evidence"      -> handleEvidence(role, caseId);
            case "workflow"      -> handleWorkflow(role, caseId, caseDesc, briefing, message, session);
            default              -> ChatResponse.text("Unbekannte Aktion: " + action);
        };
    }

    // --- Action Handlers ---

    private ChatResponse handleChat(Role role, String caseId, String caseDesc,
                                    CaseBriefing briefing,
                                    String message, HttpSession session) {
        // Schritt 3+4: Kontext aufbauen
        List<DocumentChunk> context = retrievalService.retrieve(role, caseId, "chat", message);
        storeContextInSession(session, caseId, context);

        // Schritt 5: Text generieren (inkl. Fallzustand fuer Incident-Effekte)
        CaseState caseState = workflowService.getCaseState(caseId);
        String systemPrompt = promptBuilder.buildSystemPrompt(role, caseDesc, briefing, context, "chat", caseState);
        String reply = llmService.chat(systemPrompt, message);
        List<String> sources = promptBuilder.extractSourceIds(context);

        addToHistory(session, message, reply);

        List<String> warnings = buildCaseStateWarnings(caseState);
        SecurityContext ctx = buildSecurityContext(role, "chat", caseId, context);
        return new ChatResponse(reply, sources, null, warnings, ctx);
    }

    private ChatResponse handleHandover(Role role, String caseId, String caseDesc,
                                        CaseBriefing briefing,
                                        String message, HttpSession session) {
        if (!policyEngine.canUseTool(role, "create_handover_draft")) {
            return ChatResponse.text("Zugriff verweigert: Ihre Rolle (" + role
                    + ") ist nicht berechtigt, \u00dcbergabe-Entw\u00fcrfe zu erstellen.");
        }

        List<DocumentChunk> context = retrievalService.retrieve(role, caseId, "handover", message);
        storeContextInSession(session, caseId, context);

        CaseState caseState = workflowService.getCaseState(caseId);
        String systemPrompt = promptBuilder.buildSystemPrompt(role, caseDesc, briefing, context, "handover", caseState);
        String reply = llmService.chat(systemPrompt, "Erstelle einen Sicherheits-\u00dcbergabe-Entwurf f\u00fcr diesen Fall.");
        List<String> sources = promptBuilder.extractSourceIds(context);

        ToolActionResult toolResult = workflowService.executeAction(caseId, "create_handover_draft", role);

        SecurityContext ctx = buildSecurityContext(role, "handover", caseId, context);
        return new ChatResponse(reply, sources, toolResult, buildCaseStateWarnings(caseState), ctx);
    }

    private ChatResponse handleSimilarCases(Role role, String caseId, String message) {
        if (!policyEngine.canUseTool(role, "show_similar_cases")) {
            return ChatResponse.text("Zugriff verweigert: Ihre Rolle (" + role
                    + ") ist nicht berechtigt, \u00e4hnliche F\u00e4lle einzusehen.");
        }

        List<DemoCase> similar = demoCaseService.findSimilarCases(caseId, role, message);

        StringBuilder sb = new StringBuilder("## Aehnliche Faelle\n\n");
        for (DemoCase c : similar) {
            sb.append("- **").append(c.title()).append("** (").append(c.id()).append(")\n");
            sb.append("  Typ: ").append(c.type())
              .append(" | Schweregrad: ").append(c.severity()).append("\n");
            sb.append("  ").append(c.description()).append("\n\n");
        }
        sb.append("*Gesamt: ").append(similar.size()).append(" \u00e4hnliche F\u00e4lle gefunden.*");

        // Incident-Effekt: Fallzustand auch im Similar-Cases-Pfad sichtbar
        CaseState caseState = workflowService.getCaseState(caseId);
        SecurityContext ctx = SecurityContext.of(role.name().toLowerCase(), "similar_cases", caseId);
        return new ChatResponse(sb.toString(), List.of(), null, buildCaseStateWarnings(caseState), ctx);
    }

    private ChatResponse handleEvidence(Role role, String caseId) {
        List<DocumentChunk> context = retrievalService.retrieve(role, caseId, "evidence", null);

        StringBuilder sb = new StringBuilder("## Beweise / Quellen\n\n");
        if (context.isEmpty()) {
            sb.append("Fuer den aktuellen Fall wurden in diesem Modus keine zusaetzlichen Quellen gefunden. "
                    + "Pruefen Sie die Rolle, den Fallkontext oder fuegen Sie weitere Artefakte hinzu.\n\n");
        } else {
            sb.append("Es wurden ").append(context.size())
                    .append(" Quelle(n) fuer den aktuellen Fallkontext gefunden.\n\n");
        }

        for (DocumentChunk chunk : context) {
            sb.append("### ").append(chunk.title()).append("\n");
            sb.append("- **Quelle:** ").append(chunk.docId()).append(" (").append(chunk.sourceType()).append(")\n");
            sb.append("- **Klassifizierung:** ").append(chunk.classification()).append("\n");
            sb.append("- **Vertrauensstufe:** ").append(chunk.trustLevel()).append("\n");
            sb.append("- **Inhalt:** ").append(truncate(chunk.text(), 200)).append("\n\n");
        }

        List<String> sources = promptBuilder.extractSourceIds(context);

        // Incident-Effekt: Fallzustand auch im Evidence-Pfad sichtbar
        CaseState caseState = workflowService.getCaseState(caseId);
        SecurityContext ctx = buildSecurityContext(role, "evidence", caseId, context);
        return new ChatResponse(sb.toString(), sources, null, buildCaseStateWarnings(caseState), ctx);
    }

    private ChatResponse handleWorkflow(Role role, String caseId, String caseDesc,
                                        CaseBriefing briefing,
                                        String actionName, HttpSession session) {
        if (actionName == null || actionName.isBlank()) {
            return ChatResponse.text("Bitte geben Sie eine Workflow-Aktion an oder fordern Sie eine Triage-Bewertung an.");
        }

        // Triage assessment: LLM liefert strukturierte TriageAssessment
        if (isNaturalLanguageTriageRequest(actionName)) {
            List<DocumentChunk> context = resolveContext(role, caseId, "workflow", actionName, session);
            storeContextInSession(session, caseId, context);
            List<String> sources = promptBuilder.extractSourceIds(context);
            return handleTriageAssessment(role, caseId, caseDesc, briefing, actionName, context, sources);
        }

        List<DocumentChunk> context = resolveContext(role, caseId, "workflow", null, session);
        storeContextInSession(session, caseId, context);
        List<String> sources = promptBuilder.extractSourceIds(context);

        // Direct workflow action – check tool policy
        ToolPolicyDecision decision = toolPolicyService.evaluateAccess(role, actionName, context);
        if (!decision.allowed()) {
            List<String> warnings = new ArrayList<>();
            warnings.add("\u26D4 Zugriff verweigert: Ihre Rolle (" + role
                    + ") ist nicht berechtigt, '" + actionName + "' auszuf\u00fchren."
                    + formatDecisionDetail(decision));
            SecurityContext ctx = buildSecurityContext(role, "workflow", caseId, context);
            return new ChatResponse(
                    "Zugriff verweigert: Ihre Rolle (" + role
                    + ") ist nicht berechtigt, '" + actionName + "' auszuf\u00fchren.",
                    sources, null, warnings, ctx);
        }

        ToolActionResult result = workflowService.executeAction(caseId, actionName, role);

        CaseState caseState = workflowService.getCaseState(caseId);
        String systemPrompt = promptBuilder.buildSystemPrompt(role, caseDesc, briefing, context, "workflow", caseState);
        String reply = llmService.chat(systemPrompt,
                "Die Workflow-Aktion '" + actionName + "' wurde ausgef\u00fchrt. "
                + "Fasse die Auswirkungen dieser Aktion auf den Fall zusammen.");

        List<String> warnings = new ArrayList<>(buildCaseStateWarnings(caseState));
        warnings.addAll(buildAccessDecisionWarnings(role, actionName, decision));
        warnings.addAll(buildElevatedNoteWarnings(context));

        SecurityContext ctx = buildSecurityContext(role, "workflow", caseId, context);
        return new ChatResponse(reply, sources, result, warnings, ctx);
    }

    /**
     * Handles a triage assessment: LLM liefert ein strukturiertes
     * {@link TriageAssessment} statt Freitext. Der ChatOrchestrator arbeitet
     * mit dem typisierten Objekt und vermeidet fragiles Text-Parsing.
     *
     * <p>Defensive Validierung: Nur DTO-validierte Aktionen ({@code hasValidAction()})
     * werden ueberhaupt an ToolPolicyService weitergereicht. Unbekannte oder
     * ungueltige Werte werden nie als Workflow-Aktion behandelt.</p>
     */
    private ChatResponse handleTriageAssessment(Role role, String caseId, String caseDesc,
                                                CaseBriefing briefing,
                                                String requestMessage,
                                                List<DocumentChunk> context, List<String> sources) {
        CaseState caseState = workflowService.getCaseState(caseId);
        String systemPrompt = promptBuilder.buildSystemPrompt(role, caseDesc, briefing, context, "workflow", caseState);

        // Strukturierte Triage-Bewertung statt Freitext.
        // Eine zweite LLM-Pruefung reduziert zufaellige Ausreißer, ohne die
        // Entscheidungshoheit fuer Aktionen aus dem deterministischen Code zu nehmen.
        TriageAssessment assessment = reviewAssessment(
                systemPrompt,
                caseDesc,
                llmService.assessTriage(systemPrompt, caseDesc));
        String reply = formatTriageReply(assessment);

        SecurityContext ctx = buildSecurityContext(role, "triage", caseId, context);

        // Defensive Pruefung: Nur bekannte, gueltige Aktionen weiterverarbeiten
        if (assessment.hasValidAction()) {
            String suggestedAction = assessment.recommendedAction();
            ToolPolicyDecision decision = toolPolicyService.evaluateAccess(role, suggestedAction, context);

            if (decision.allowed()) {
                ToolActionResult result = workflowService.executeAction(caseId, suggestedAction, role);

                // Incident-Effekt: aktualisierter Zustand nach Aktion
                CaseState updatedState = workflowService.getCaseState(caseId);
                List<String> warnings = new ArrayList<>(buildCaseStateWarnings(updatedState));
                warnings.addAll(buildAccessDecisionWarnings(role, suggestedAction, decision));
                warnings.addAll(buildElevatedNoteWarnings(context));
                return new ChatResponse(reply, sources, result, warnings, ctx);
            }

            List<String> warnings = new ArrayList<>();
            warnings.add("\u26D4 Zugriff verweigert: Die vorgeschlagene Aktion '"
                    + suggestedAction + "' erfordert erh\u00f6hte Berechtigungen."
                    + formatDecisionDetail(decision));
            return new ChatResponse(reply, sources, null, warnings, ctx);
        }

        return new ChatResponse(reply, sources, null, List.of(), ctx);
    }

    /**
     * Fuehrt eine initiale Triage-Bewertung und eine challengende Zweitbewertung
     * zusammen, um Modellvarianz zu reduzieren.
     *
     * <p>Wichtig: Diese Logik ist keine Security-Grenze. Sie stabilisiert nur die
     * LLM-Ausgabe. Die endgueltige Freigabe einer Aktion bleibt vollstaendig bei
     * {@link ToolPolicyService}.</p>
     */
    private TriageAssessment reviewAssessment(String systemPrompt,
                                              String caseDesc,
                                              TriageAssessment initialAssessment) {
        TriageAssessment initial = initialAssessment != null
                ? initialAssessment.sanitized()
                : TriageAssessment.FALLBACK;
        TriageAssessment challenged = llmService.reviewTriage(systemPrompt, caseDesc, initial).sanitized();

        String agreedAction = agreedAction(initial, challenged);
        String riskLevel = moreSevereRisk(initial.riskLevel(), challenged.riskLevel());
        double confidence = agreedAction != null
                ? Math.min(initial.confidence(), challenged.confidence())
                : Math.min(initial.confidence(), challenged.confidence()) * 0.5;

        String summary = challenged.summary() != null && !challenged.summary().isBlank()
                ? challenged.summary()
                : initial.summary();
        String evidenceAssessment = mergeEvidence(initial.evidenceAssessment(), challenged.evidenceAssessment());

        return new TriageAssessment(summary, riskLevel, agreedAction, confidence, evidenceAssessment).sanitized();
    }

    private String agreedAction(TriageAssessment initial, TriageAssessment challenged) {
        if (initial.hasValidAction()
                && challenged.hasValidAction()
                && initial.recommendedAction().equals(challenged.recommendedAction())) {
            return initial.recommendedAction();
        }
        return null;
    }

    private String moreSevereRisk(String left, String right) {
        return severityRank(left) >= severityRank(right) ? left : right;
    }

    private int severityRank(String riskLevel) {
        return switch (riskLevel) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private String mergeEvidence(String initialEvidence, String challengedEvidence) {
        if (challengedEvidence == null || challengedEvidence.isBlank()) {
            return initialEvidence;
        }
        if (initialEvidence == null || initialEvidence.isBlank()) {
            return challengedEvidence;
        }
        if (challengedEvidence.equals(initialEvidence)) {
            return challengedEvidence;
        }
        return challengedEvidence + " Konsistenzcheck: " + initialEvidence;
    }

    /**
     * Formatiert ein TriageAssessment als lesbaren Text fuer die Antwort.
     */
    private String formatTriageReply(TriageAssessment assessment) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Triage-Bewertung\n\n");
        sb.append(assessment.summary()).append("\n\n");
        sb.append("**Risikostufe:** ").append(assessment.riskLevel()).append("\n");
        sb.append("**Konfidenz:** ").append(String.format("%.0f%%", assessment.confidence() * 100)).append("\n");
        sb.append("**Beweisl\u00e4ge:** ").append(assessment.evidenceAssessment()).append("\n");
        if (assessment.recommendedAction() != null) {
            sb.append("\n**Empfohlene Aktion:** ").append(assessment.recommendedAction()).append("\n");
        }
        return sb.toString();
    }

    // --- Session & Context Management ---

    /**
     * Behandelt Rollenwechsel in der Session. Loescht Session-Daten,
     * um Kontext-Leaks zwischen Rollen zu verhindern.
     */
    private void handleRoleChange(HttpSession session, Role newRole) {
        Role oldRole = (Role) session.getAttribute(SESSION_ROLE);
        session.setAttribute(SESSION_ROLE, newRole);

        if (oldRole != null && oldRole != newRole) {
            log.debug("Role changed from {} to {} \u2013 clearing session data", oldRole, newRole);
            session.removeAttribute(SESSION_HISTORY);
            session.removeAttribute(SESSION_LAST_CONTEXT);
            session.removeAttribute(SESSION_LAST_CASE_ID);
        }
    }

    /**
     * Laedt den Kontext fuer den aktuellen Request frisch ueber den RetrievalService.
     */
    private List<DocumentChunk> resolveContext(Role role, String caseId,
                                              String mode, String query, HttpSession session) {
        return retrievalService.retrieve(role, caseId, mode, query);
    }

    /**
     * Erkennt natuerliche Triage-Anfragen aus der Konversations-API.
     * Direkte Tool-Aktionsnamen werden hiervon bewusst ausgeschlossen.
     */
    private boolean isNaturalLanguageTriageRequest(String actionName) {
        return !policyEngine.canUseTool(Role.SECURITY_ANALYST, actionName);
    }

    @SuppressWarnings("unchecked")
    private void addToHistory(HttpSession session, String userMessage, String assistantReply) {
        List<String> history = (List<String>) session.getAttribute(SESSION_HISTORY);
        if (history == null) {
            history = new ArrayList<>();
        }
        history.add("User: " + userMessage);
        history.add("Assistant: " + truncate(assistantReply, 500));
        session.setAttribute(SESSION_HISTORY, history);
    }

    private void storeContextInSession(HttpSession session, String caseId,
                                        List<DocumentChunk> context) {
        session.setAttribute(SESSION_LAST_CONTEXT, context);
        session.setAttribute(SESSION_LAST_CASE_ID, caseId);
    }

    /**
     * Erzeugt Warnungen fuer die Zugriffsentscheidung bei evidenzbasierter Freigabe.
     */
    private List<String> buildAccessDecisionWarnings(Role role, String action,
                                                     ToolPolicyDecision decision) {
        if (!"evidence_override".equals(decision.reason())) {
            return List.of();
        }
        String roleName = switch (role) {
            case EMPLOYEE -> "Mitarbeiter";
            case CONTRACTOR -> "Externer";
            case SECURITY_ANALYST -> "Security-Analyst";
        };
        return List.of("\u2699 Zugriffsentscheidung: Aktion '" + action
                + "' f\u00fcr Rolle '" + roleName
                + "' durch Evidenz-Score freigegeben (Score: "
                + decision.evidenceScore() + ", Schwellwert: "
                + decision.evidenceThreshold() + ")");
    }

    /**
     * Warnt nur, wenn Benutzernotizen mit unangemessen hohem Trust-Level im
     * Kontext vorhanden sind (Bug 5 – RAG Poisoning).
     */
    private List<String> buildElevatedNoteWarnings(List<DocumentChunk> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }

        long externalReplies = context.stream()
                .filter(c -> "case_note".equals(c.sourceType())
                        && c.docId() != null
                        && c.docId().startsWith("external_feedback_")
                        && "high".equals(c.trustLevel()))
                .count();
        if (externalReplies > 0) {
            return List.of("⚠ " + externalReplies
                    + " externe Rueckmeldung(en) als vertrauenswuerdige interne Quelle im Kontext");
        }

        long highTrustNotes = context.stream()
                .filter(c -> ("case_note".equals(c.sourceType()) || "user_note".equals(c.sourceType()))
                        && "high".equals(c.trustLevel()))
                .count();

        if (highTrustNotes > 0) {
            return List.of("⚠ " + highTrustNotes
                    + " Benutzernotiz(en) als vertrauenswuerdige interne Quelle im Kontext");
        }

        return List.of();
    }

    /**
     * Formatiert Score-Details fuer Ablehnungsmeldungen.
     */
    private String formatDecisionDetail(ToolPolicyDecision decision) {
        if ("insufficient_evidence".equals(decision.reason())) {
            return " (Evidenz-Score: " + decision.evidenceScore()
                    + ", ben\u00f6tigt: " + decision.evidenceThreshold() + ")";
        }
        return "";
    }

    /**
     * Erzeugt Warnungen fuer aktive Incident-Effekte auf dem Fall.
     */
    private List<String> buildCaseStateWarnings(CaseState state) {
        if (state == null || !state.hasActiveEffects()) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        if (state.escalationSuppressed()) {
            warnings.add("\u26A0 Sicherheitseskalation ist f\u00fcr diesen Fall UNTERDRUeCKT.");
        }
        if (state.priorityLow()) {
            warnings.add("\u26A0 Fallpriorit\u00e4t wurde auf NIEDRIG gesetzt.");
        }
        if (state.routedToFinance()) {
            warnings.add("\u26A0 Fall an Finanzabteilung weitergeleitet (aus Sicherheits-Triage entfernt).");
        }
        if (state.trustNoteAttached()) {
            warnings.add("\u26A0 Lieferanten-Vertrauensnotiz angeh\u00e4ngt (reduzierte k\u00fcnftige Pr\u00fcfung).");
        }
        return warnings;
    }

    /**
     * Baut den Verarbeitungskontext aus Rolle, Aktion und den
     * verwendeten Retrieval-Chunks.
     */
    private SecurityContext buildSecurityContext(Role role, String action, String caseId,
                                                 List<DocumentChunk> context) {
        List<SourceMeta> sourceMetas = context.stream()
                .map(c -> new SourceMeta(c.docId(), c.title(), c.trustLevel(), c.sourceType(), c.classification()))
                .toList();
        return new SecurityContext(role.name().toLowerCase(), action, caseId, sourceMetas);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String describeCase(DemoCase demoCase, CaseBriefing briefing, String caseId) {
        if (briefing != null) {
            return briefing.title() + " – " + briefing.summary();
        }
        if (demoCase != null) {
            return demoCase.title() + " – " + demoCase.description();
        }
        return "Unbekannter Fall: " + caseId;
    }
}
