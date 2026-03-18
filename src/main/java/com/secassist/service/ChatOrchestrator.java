package com.secassist.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Orchestriert den vollstaendigen Request-Ablauf gemaess "Berechtigung vor Kontext".
 *
 * <p>Die Klasse verbindet Policy-Pruefung, Retrieval, Prompt-Aufbau,
 * Modellaufruf und optionalen Workflow-Pfad zu einem klaren, sequenziellen
 * Gesamtprozess. Sie ist damit die fachliche Zentrale der Anwendung und macht
 * sichtbar, in welcher Reihenfolge Sicherheits- und Komfortlogik zusammenspielen.</p>
 *
 * <p>Ablauf:
 * <ol>
 *   <li>Actor bestimmen (Rolle)</li>
 *   <li>Zweck / Aktion bestimmen</li>
 *   <li>Erlaubte Quellen bestimmen (Policy)</li>
 *   <li>Kontext aufbauen (Retrieval)</li>
 *   <li>Text generieren oder Tool-Vorschlag erstellen</li>
 * </ol></p>
 *
 * <p>Die Schwachstellen des Workshops werden hier nicht versteckt, sondern in
 * ihren Auswirkungen sichtbar: zu breite Kontexte, zu grosszuegige
 * Workflow-Freigaben und manipulierter Fallzustand. Gerade dadurch eignet sich
 * der Orchestrator gut als Lesepunkt fuer Reviews und Blue-Team-Fixes.</p>
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

        List<String> warnings = new ArrayList<>(buildCaseStateWarnings(caseState));
        warnings.addAll(buildTrustMergeWarnings(context));
        SecurityContext ctx = buildSecurityContext(
                role,
                "chat",
                caseId,
                context,
                "policy_filtered_context",
                "Kontext wurde nach Rolle, Fall und Modus zusammengestellt.",
                null,
                null);
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

        ToolActionResult toolResult = workflowService.executeAction(
                caseId,
                "create_handover_draft",
                role,
                context,
                "role_authorized");

        List<String> warnings = new ArrayList<>(buildCaseStateWarnings(caseState));
        warnings.addAll(buildClassificationViolationWarnings(role, context));
        warnings.addAll(buildTrustMergeWarnings(context));
        SecurityContext ctx = buildSecurityContext(
                role,
                "handover",
                caseId,
                context,
                "role_authorized",
                "Handover wurde ueber regulaere Rollenfreigabe vorbereitet.",
                null,
                null);
        return new ChatResponse(reply, sources, toolResult, warnings, ctx);
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
        List<String> warnings = new ArrayList<>(buildCaseStateWarnings(caseState));
        warnings.addAll(buildOracleLeakWarnings(role, similar));
        SecurityContext ctx = new SecurityContext(
                role.name().toLowerCase(),
                "similar_cases",
                caseId,
                List.of(),
                "similarity_lookup",
                "Aehnliche Faelle wurden aus dem freigegebenen Fallkatalog und moeglichen Korrelationssignalen zusammengestellt.",
                null,
                null);
        return new ChatResponse(sb.toString(), List.of(), null, warnings, ctx);
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
        SecurityContext ctx = buildSecurityContext(
                role,
                "evidence",
                caseId,
                context,
                "policy_filtered_context",
                "Evidence-Ansicht listet die aktuell fuer Rolle und Fall freigegebenen Quellen.",
                null,
                null);
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
            List<DocumentChunk> context = resolveWorkflowContext(role, caseId, actionName, session);
            storeContextInSession(session, caseId, context);
            List<String> sources = promptBuilder.extractSourceIds(context);
            return handleTriageAssessment(role, caseId, caseDesc, briefing, actionName, context, sources);
        }

        List<DocumentChunk> context = resolveWorkflowContext(role, caseId, null, session);
        storeContextInSession(session, caseId, context);
        List<String> sources = promptBuilder.extractSourceIds(context);

        // Direct workflow action - check tool policy
        ToolPolicyDecision decision = toolPolicyService.evaluateAccess(role, actionName, context);
        if (!decision.allowed()) {
            List<String> warnings = new ArrayList<>();
            warnings.add("\u26D4 Zugriff verweigert: Ihre Rolle (" + role
                    + ") ist nicht berechtigt, '" + actionName + "' auszuf\u00fchren."
                    + formatDecisionDetail(decision));
            SecurityContext ctx = buildSecurityContext(
                    role,
                    "workflow",
                    caseId,
                    context,
                    decision.reason(),
                    buildDecisionSummary(actionName, decision),
                    nullableScore(decision),
                    nullableThreshold(decision));
            return new ChatResponse(
                    "Zugriff verweigert: Ihre Rolle (" + role
                    + ") ist nicht berechtigt, '" + actionName + "' auszuf\u00fchren.",
                    sources, null, warnings, ctx);
        }

        ToolActionResult result = workflowService.executeAction(caseId, actionName, role, context, decision.reason());

        CaseState caseState = workflowService.getCaseState(caseId);
        String systemPrompt = promptBuilder.buildSystemPrompt(role, caseDesc, briefing, context, "workflow", caseState);
        String reply = llmService.chat(systemPrompt,
                "Die Workflow-Aktion '" + actionName + "' wurde ausgef\u00fchrt. "
                + "Fasse die Auswirkungen dieser Aktion auf den Fall zusammen.");

        List<String> warnings = new ArrayList<>(buildCaseStateWarnings(caseState));
        warnings.addAll(buildAccessDecisionWarnings(role, actionName, decision, context));
        warnings.addAll(buildElevatedNoteWarnings(context));
        warnings.addAll(buildTrustMergeWarnings(context));

        SecurityContext ctx = buildSecurityContext(
                role,
                "workflow",
                caseId,
                context,
                decision.reason(),
                buildDecisionSummary(actionName, decision),
                nullableScore(decision),
                nullableThreshold(decision));
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
        // Eine zweite LLM-Pruefung reduziert zufaellige Ausreisser, ohne die
        // Entscheidungshoheit fuer Aktionen aus dem deterministischen Code zu nehmen.
        TriageAssessment assessment = reviewAssessment(
                systemPrompt,
                caseDesc,
                llmService.assessTriage(systemPrompt, caseDesc));
        String reply = formatTriageReply(assessment);

        SecurityContext ctx = buildSecurityContext(
                role,
                "triage",
                caseId,
                context,
                "triage_assessment",
                "Triage-Einschaetzung wurde auf Basis des aktuellen Rollen- und Retrieval-Kontexts erstellt.",
                null,
                null);

        // Defensive Pruefung: Nur bekannte, gueltige Aktionen weiterverarbeiten
        if (assessment.hasValidAction()) {
            String suggestedAction = assessment.recommendedAction();
            ToolPolicyDecision decision = toolPolicyService.evaluateAccess(role, suggestedAction, context);

            if (decision.allowed()) {
                ToolActionResult result = workflowService.executeAction(
                        caseId,
                        suggestedAction,
                        role,
                        context,
                        decision.reason());

                // Incident-Effekt: aktualisierter Zustand nach Aktion
                CaseState updatedState = workflowService.getCaseState(caseId);
                List<String> warnings = new ArrayList<>(buildCaseStateWarnings(updatedState));
                warnings.addAll(buildAccessDecisionWarnings(role, suggestedAction, decision, context));
                warnings.addAll(buildElevatedNoteWarnings(context));
                warnings.addAll(buildTrustMergeWarnings(context));
                SecurityContext decidedContext = buildSecurityContext(
                        role,
                        "triage",
                        caseId,
                        context,
                        decision.reason(),
                        buildDecisionSummary(suggestedAction, decision),
                        nullableScore(decision),
                        nullableThreshold(decision));
                return new ChatResponse(reply, sources, result, warnings, decidedContext);
            }

            List<String> warnings = new ArrayList<>();
            warnings.add("\u26D4 Zugriff verweigert: Die vorgeschlagene Aktion '"
                    + suggestedAction + "' erfordert erh\u00f6hte Berechtigungen."
                    + formatDecisionDetail(decision));
            SecurityContext deniedContext = buildSecurityContext(
                    role,
                    "triage",
                    caseId,
                    context,
                    decision.reason(),
                    buildDecisionSummary(suggestedAction, decision),
                    nullableScore(decision),
                    nullableThreshold(decision));
            return new ChatResponse(reply, sources, null, warnings, deniedContext);
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
                                               String mode, String query) {
        return retrievalService.retrieve(role, caseId, mode, query);
    }

    /**
     * Baut fuer Workflow-Pfade einen kleinen "working context" auf.
     *
     * <p>Plausible Produktlogik: Nach einer vorangegangenen Analyse arbeitet die
     * App mit dem zuletzt sichtbaren Arbeitskontext weiter, statt fuer jede
     * Folgetriage bei null zu beginnen. Genau dieses Verhalten macht mehrstufige
     * Ausnutzungspfade realistischer und weniger button-getrieben.</p>
     */
    @SuppressWarnings("unchecked")
    private List<DocumentChunk> resolveWorkflowContext(Role role,
                                                       String caseId,
                                                       String query,
                                                       HttpSession session) {
        List<DocumentChunk> freshContext = resolveContext(role, caseId, "workflow", query);
        List<DocumentChunk> previousContext = List.of();
        Object storedCaseId = session.getAttribute(SESSION_LAST_CASE_ID);
        Object storedContext = session.getAttribute(SESSION_LAST_CONTEXT);
        if (caseId != null
                && caseId.equals(storedCaseId)
                && storedContext instanceof List<?> storedList) {
            previousContext = (List<DocumentChunk>) storedList;
        }
        return mergeContexts(previousContext, freshContext);
    }

    private List<DocumentChunk> mergeContexts(List<DocumentChunk> primary,
                                              List<DocumentChunk> secondary) {
        Map<String, DocumentChunk> merged = new LinkedHashMap<>();
        if (primary != null) {
            for (DocumentChunk chunk : primary) {
                merged.put(chunk.id(), chunk);
            }
        }
        if (secondary != null) {
            for (DocumentChunk chunk : secondary) {
                merged.putIfAbsent(chunk.id(), chunk);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * Erkennt natuerliche Triage-Anfragen aus der Konversations-API.
     * Direkte Tool-Aktionsnamen werden hiervon bewusst ausgeschlossen.
     */
    private boolean isNaturalLanguageTriageRequest(String actionName) {
        return actionName == null || !TriageAssessment.KNOWN_ACTIONS.contains(actionName);
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

    // --- Produktnahe Hinweiserzeugung ---

    /**
     * Erkennt Klassifizierungsverletzungen: Chunks mit zu hoher Klassifizierung
     * fuer die aktuelle Rolle (Bug 1 - Handover Scope Leak).
     */
    private List<String> buildClassificationViolationWarnings(Role role, List<DocumentChunk> context) {
        if (context == null || context.isEmpty() || role == Role.SECURITY_ANALYST) {
            return List.of();
        }
        Set<String> allowed = policyEngine.allowedClassifications(role);
        List<DocumentChunk> violations = context.stream()
                .filter(c -> !allowed.contains(c.classification()))
                .toList();
        if (violations.isEmpty()) {
            return List.of();
        }
        String sources = violations.stream()
                .map(DocumentChunk::title)
                .limit(3)
                .reduce((left, right) -> left + ", " + right)
                .orElse("eingeschraenkte Quelle");
        return List.of("Arbeitsnotiz: Der Handover greift auf Material oberhalb der ueblichen Sichtgrenze dieser Rolle zu ("
                + sources + ").");
    }

    /**
     * Erkennt Metadaten-Leaks durch die Aehnliche-Faelle-Suche
     * (Bug 2 - Existence Oracle).
     */
    private List<String> buildOracleLeakWarnings(Role role, List<DemoCase> similarCases) {
        if (role == Role.SECURITY_ANALYST) {
            return List.of();
        }
        boolean hasSignal = similarCases.stream()
                .anyMatch(c -> "internal_similarity_signal".equals(c.id()));
        if (!hasSignal) {
            return List.of();
        }
        return List.of("Arbeitsnotiz: Die Similarity-Konfidenz wurde durch ein Korrelationssignal aus eingeschraenktem Praezedenzkorpus beeinflusst.");
    }

    /**
     * Erkennt Trust-Boundary-Verletzungen: Trusted und untrusted Quellen
     * im selben Kontext (Bug 4 - Trust Merge).
     */
    private List<String> buildTrustMergeWarnings(List<DocumentChunk> context) {
        if (context == null || context.size() < 2) {
            return List.of();
        }
        long trusted = context.stream()
                .filter(c -> "high".equals(c.trustLevel())
                        && ("policy".equals(c.sourceType()) || "runbook".equals(c.sourceType())
                            || "postmortem".equals(c.sourceType()) || "helpdesk_guide".equals(c.sourceType())))
                .count();
        long untrusted = context.stream()
                .filter(c -> "untrusted".equals(c.trustLevel()))
                .count();
        if (trusted == 0 || untrusted == 0) {
            return List.of();
        }
        return List.of("Kontext-Hinweis: Kuratierte interne Guidance und ungepruefte Fremdaussagen wurden gemeinsam in die Bewertung eingeblendet. Ergebnisse nur mit Verifikationsvorbehalt verwenden.");
    }

    /**
     * Erzeugt Warnungen fuer die Zugriffsentscheidung bei evidenzbasierter Freigabe.
     */
    private List<String> buildAccessDecisionWarnings(Role role, String action,
                                                     ToolPolicyDecision decision) {
        return buildAccessDecisionWarnings(role, action, decision, List.of());
    }

    /**
     * Erzeugt Warnungen fuer die Zugriffsentscheidung bei evidenzbasierter Freigabe
     * mit optionaler Quellenauflistung.
     */
    private List<String> buildAccessDecisionWarnings(Role role, String action,
                                                     ToolPolicyDecision decision,
                                                     List<DocumentChunk> context) {
        if (!"evidence_override".equals(decision.reason())) {
            return List.of();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Arbeitsjournal: Aktion '")
          .append(action).append("' wurde ueber eine beschleunigte evidenzbasierte Freigabe vorbereitet (Score: ")
          .append(decision.evidenceScore()).append(", Schwellwert: ")
          .append(decision.evidenceThreshold())
          .append(").");

        if (!context.isEmpty()) {
            sb.append(" Kontextimpulse:");
            for (DocumentChunk c : context) {
                if ("policy".equals(c.sourceType()) || "runbook".equals(c.sourceType())
                        || "case_note".equals(c.sourceType()) || "supplier_note".equals(c.sourceType())) {
                    sb.append(" [").append(c.title())
                      .append(" (trust=").append(c.trustLevel()).append(")]");
                }
            }
        }
        return List.of(sb.toString());
    }

    /**
     * Warnt nur, wenn Benutzernotizen mit unangemessen hohem Trust-Level im
     * Kontext vorhanden sind (Bug 5 - RAG Poisoning).
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
            return List.of("Kontext-Hinweis: Eingehende Partner-Rueckmeldungen fliessen derzeit wie interne Fallinformation in die Bewertung ein.");
        }

        long highTrustNotes = context.stream()
                .filter(c -> ("case_note".equals(c.sourceType()) || "user_note".equals(c.sourceType()))
                        && "high".equals(c.trustLevel()))
                .count();

        if (highTrustNotes > 0) {
            return List.of("Kontext-Hinweis: Zusaetzliche Fallnotizen werden im aktuellen Kontext wie interne Arbeitsinformation gewichtet.");
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
            warnings.add("Arbeitsstatus: Sicherheitseskalation derzeit ausgesetzt.");
        }
        if (state.priorityLow()) {
            warnings.add("Arbeitsstatus: Fallprioritaet aktuell auf niedrig gesetzt.");
        }
        if (state.routedToFinance()) {
            warnings.add("Arbeitsstatus: Fall liegt aktuell in der Finanz-Queue und nicht mehr in der Security-Triage.");
        }
        if (state.trustNoteAttached()) {
            warnings.add("Arbeitsstatus: Lieferanten-Vertrauensnotiz ist fuer Folgebewertungen aktiv.");
        }
        return warnings;
    }

    /**
     * Baut den Verarbeitungskontext aus Rolle, Aktion und den
     * verwendeten Retrieval-Chunks.
     */
    private SecurityContext buildSecurityContext(Role role,
                                                 String action,
                                                 String caseId,
                                                 List<DocumentChunk> context,
                                                 String decisionPath,
                                                 String decisionSummary,
                                                 Integer evidenceScore,
                                                 Integer evidenceThreshold) {
        List<SourceMeta> sourceMetas = context.stream()
                .map(c -> new SourceMeta(
                        c.docId(),
                        c.title(),
                        c.trustLevel(),
                        c.sourceType(),
                        c.classification(),
                        determineOrigin(c),
                        determineIncludedBy(c, action),
                        determineInfluence(c)))
                .toList();
        return new SecurityContext(
                role.name().toLowerCase(),
                action,
                caseId,
                sourceMetas,
                decisionPath,
                decisionSummary,
                evidenceScore,
                evidenceThreshold);
    }

    private String buildDecisionSummary(String action, ToolPolicyDecision decision) {
        return switch (decision.reason()) {
            case "role_authorized" -> "Aktion '" + action + "' wurde ueber regulaere Rollenfreigabe bearbeitet.";
            case "evidence_override" -> "Aktion '" + action + "' wurde ueber evidenzbasierte Schnellfreigabe bearbeitet.";
            case "insufficient_evidence" -> "Aktion '" + action + "' blieb unterhalb der aktuellen Evidenzschwelle.";
            default -> "Aktion '" + action + "' ist fuer diese Rolle nicht vorgesehen.";
        };
    }

    private Integer nullableScore(ToolPolicyDecision decision) {
        return decision.evidenceScore() > 0 ? decision.evidenceScore() : null;
    }

    private Integer nullableThreshold(ToolPolicyDecision decision) {
        return decision.evidenceThreshold() > 0 ? decision.evidenceThreshold() : null;
    }

    private String determineOrigin(DocumentChunk chunk) {
        if (chunk.docId() != null && chunk.docId().startsWith("external_feedback_")) {
            return "external_feedback";
        }
        return switch (chunk.sourceType()) {
            case "policy", "runbook", "helpdesk_guide" -> "internal_document";
            case "postmortem" -> "internal_incident_history";
            case "supplier_note" -> "supplier_input";
            case "case_note", "user_note" -> "case_journal";
            default -> chunk.sourceType();
        };
    }

    private String determineIncludedBy(DocumentChunk chunk, String action) {
        if (chunk.docId() != null && chunk.docId().startsWith("external_feedback_")) {
            return "dynamic_feedback_entry";
        }
        if ("case_note".equals(chunk.sourceType()) || "user_note".equals(chunk.sourceType())) {
            return "dynamic_case_note";
        }
        if ("supplier_note".equals(chunk.sourceType())) {
            return "case_artifact_match";
        }
        if ("handover".equals(action) && "confidential".equals(chunk.classification())) {
            return "expanded_handover_context";
        }
        return "policy_filtered_retrieval";
    }

    private String determineInfluence(DocumentChunk chunk) {
        if ("untrusted".equals(chunk.trustLevel())) {
            return "cautionary_context";
        }
        if ("high".equals(chunk.trustLevel())
                && ("policy".equals(chunk.sourceType())
                || "runbook".equals(chunk.sourceType())
                || "postmortem".equals(chunk.sourceType())
                || "helpdesk_guide".equals(chunk.sourceType()))) {
            return "high_guidance_weight";
        }
        if ("case_note".equals(chunk.sourceType()) || "user_note".equals(chunk.sourceType())) {
            return "operational_context";
        }
        return "supporting_context";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String describeCase(DemoCase demoCase, CaseBriefing briefing, String caseId) {
        if (briefing != null) {
            return briefing.title() + " - " + briefing.summary();
        }
        if (demoCase != null) {
            return demoCase.title() + " - " + demoCase.description();
        }
        return "Unbekannter Fall: " + caseId;
    }
}
