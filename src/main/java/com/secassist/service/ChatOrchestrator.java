package com.secassist.service;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secassist.config.BugFlagsProperties;
import com.secassist.llm.LlmService;
import com.secassist.model.ChatRequest;
import com.secassist.model.ChatResponse;
import com.secassist.model.DemoCase;
import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;
import com.secassist.model.ToolActionResult;
import com.secassist.policy.PolicyEngine;
import com.secassist.retrieval.RetrievalService;
import com.secassist.tools.IncidentWorkflowService;
import com.secassist.tools.ToolPolicyService;

/**
 * Orchestriert den gesamten Request-Ablauf gemäß „Berechtigung vor Kontext".
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

    private final PolicyEngine policyEngine;
    private final RetrievalService retrievalService;
    private final LlmService llmService;
    private final ToolPolicyService toolPolicyService;
    private final IncidentWorkflowService workflowService;
    private final DemoCaseService demoCaseService;
    private final PromptBuilder promptBuilder;
    private final BugFlagsProperties bugFlags;

    public ChatOrchestrator(PolicyEngine policyEngine,
                            RetrievalService retrievalService,
                            LlmService llmService,
                            ToolPolicyService toolPolicyService,
                            IncidentWorkflowService workflowService,
                            DemoCaseService demoCaseService,
                            PromptBuilder promptBuilder,
                            BugFlagsProperties bugFlags) {
        this.policyEngine = policyEngine;
        this.retrievalService = retrievalService;
        this.llmService = llmService;
        this.toolPolicyService = toolPolicyService;
        this.workflowService = workflowService;
        this.demoCaseService = demoCaseService;
        this.promptBuilder = promptBuilder;
        this.bugFlags = bugFlags;
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

        // Schritt 2: Aktion bestimmen
        String action = request.action() != null ? request.action() : "chat";
        String caseId = request.caseId();
        String message = request.message() != null ? request.message() : "";

        // Fallinfo laden
        DemoCase demoCase = demoCaseService.findById(caseId);
        String caseDesc = demoCase != null ? demoCase.description() : "Unknown case: " + caseId;

        return switch (action) {
            case "chat"          -> handleChat(role, caseId, caseDesc, message, session);
            case "handover"      -> handleHandover(role, caseId, caseDesc, session);
            case "similar_cases" -> handleSimilarCases(role, caseId);
            case "evidence"      -> handleEvidence(role, caseId);
            case "workflow"      -> handleWorkflow(role, caseId, caseDesc, message, session);
            default              -> ChatResponse.text("Unknown action: " + action);
        };
    }

    // --- Action Handlers ---

    private ChatResponse handleChat(Role role, String caseId, String caseDesc,
                                    String message, HttpSession session) {
        // Schritt 3+4: Kontext aufbauen
        List<DocumentChunk> context = retrievalService.retrieve(role, caseId, "chat", message);
        storeContextInSession(session, context);

        // Schritt 5: Text generieren
        String systemPrompt = promptBuilder.buildSystemPrompt(role, caseDesc, context, "chat");
        String reply = llmService.chat(systemPrompt, message);
        List<String> sources = promptBuilder.extractSourceIds(context);

        addToHistory(session, message, reply);

        return ChatResponse.withSources(reply, sources);
    }

    private ChatResponse handleHandover(Role role, String caseId, String caseDesc,
                                        HttpSession session) {
        if (!policyEngine.canUseTool(role, "create_handover_draft")) {
            return ChatResponse.text("Access denied: your role (" + role
                    + ") is not authorized to create handover drafts.");
        }

        List<DocumentChunk> context = resolveContext(role, caseId, "handover", session);
        storeContextInSession(session, context);

        String systemPrompt = promptBuilder.buildSystemPrompt(role, caseDesc, context, "handover");
        String reply = llmService.chat(systemPrompt, "Create a security handover draft for this case.");
        List<String> sources = promptBuilder.extractSourceIds(context);

        ToolActionResult toolResult = workflowService.executeAction(caseId, "create_handover_draft", role);

        return new ChatResponse(reply, sources, toolResult, List.of());
    }

    private ChatResponse handleSimilarCases(Role role, String caseId) {
        if (!policyEngine.canUseTool(role, "show_similar_cases")) {
            return ChatResponse.text("Access denied: your role (" + role
                    + ") is not authorized to view similar cases.");
        }

        List<DemoCase> similar = demoCaseService.findSimilarCases(caseId, role);

        StringBuilder sb = new StringBuilder("## Similar Cases\n\n");
        for (DemoCase c : similar) {
            sb.append("- **").append(c.title()).append("** (").append(c.id()).append(")\n");
            sb.append("  Type: ").append(c.type())
              .append(" | Severity: ").append(c.severity()).append("\n");
            sb.append("  ").append(c.description()).append("\n\n");
        }
        sb.append("*Total: ").append(similar.size()).append(" similar cases found.*");

        return ChatResponse.text(sb.toString());
    }

    private ChatResponse handleEvidence(Role role, String caseId) {
        List<DocumentChunk> context = retrievalService.retrieve(role, caseId, "evidence", null);

        StringBuilder sb = new StringBuilder("## Evidence / Sources\n\n");
        for (DocumentChunk chunk : context) {
            sb.append("### ").append(chunk.title()).append("\n");
            sb.append("- **Source:** ").append(chunk.docId()).append(" (").append(chunk.sourceType()).append(")\n");
            sb.append("- **Classification:** ").append(chunk.classification()).append("\n");
            sb.append("- **Trust Level:** ").append(chunk.trustLevel()).append("\n");
            sb.append("- **Content:** ").append(truncate(chunk.text(), 200)).append("\n\n");
        }

        List<String> sources = promptBuilder.extractSourceIds(context);
        return ChatResponse.withSources(sb.toString(), sources);
    }

    private ChatResponse handleWorkflow(Role role, String caseId, String caseDesc,
                                        String actionName, HttpSession session) {
        if (actionName == null || actionName.isBlank()) {
            return ChatResponse.text("Please specify a workflow action or request a triage assessment.");
        }

        List<DocumentChunk> context = resolveContext(role, caseId, "workflow", session);
        storeContextInSession(session, context);
        List<String> sources = promptBuilder.extractSourceIds(context);

        // Triage assessment: LLM analyzes the case and suggests an action
        if ("triage".equals(actionName)) {
            return handleTriageAssessment(role, caseId, caseDesc, context, sources);
        }

        // Direct workflow action – check tool policy
        if (!toolPolicyService.isToolAllowed(role, actionName, context)) {
            return ChatResponse.text("Access denied: your role (" + role
                    + ") is not authorized to execute '" + actionName + "'.");
        }

        ToolActionResult result = workflowService.executeAction(caseId, actionName, role);

        String systemPrompt = promptBuilder.buildSystemPrompt(role, caseDesc, context, "workflow");
        String reply = llmService.chat(systemPrompt,
                "The workflow action '" + actionName + "' has been executed. "
                + "Summarize the implications of this action for the case.");

        List<String> warnings = new ArrayList<>();
        if (result.executed()) {
            warnings.add("Workflow action '" + actionName + "' was executed on case '" + caseId + "'.");
        }

        return new ChatResponse(reply, sources, result, warnings);
    }

    /**
     * Handles a triage assessment: asks the LLM to analyze the case and suggest
     * an appropriate workflow action based on the retrieved context.
     */
    private ChatResponse handleTriageAssessment(Role role, String caseId, String caseDesc,
                                                List<DocumentChunk> context, List<String> sources) {
        String systemPrompt = promptBuilder.buildSystemPrompt(role, caseDesc, context, "workflow");
        String reply = llmService.chat(systemPrompt, "Assess this case and recommend a triage action.");

        String suggestedAction = extractSuggestedAction(reply);

        if (suggestedAction != null && toolPolicyService.isToolAllowed(role, suggestedAction, context)) {
            ToolActionResult result = workflowService.executeAction(caseId, suggestedAction, role);
            List<String> warnings = new ArrayList<>();
            if (result.executed()) {
                warnings.add("Triage assessment led to execution of '" + suggestedAction
                        + "' on case '" + caseId + "'.");
            }
            return new ChatResponse(reply, sources, result, warnings);
        }

        if (suggestedAction != null) {
            return ChatResponse.withSources(reply + "\n\n*Note: The suggested action '"
                    + suggestedAction + "' requires elevated privileges.*", sources);
        }

        return ChatResponse.withSources(reply, sources);
    }

    // --- Session & Context Management ---

    /**
     * Handles role changes within the session. Preserves session continuity
     * across role switches to maintain conversation context.
     */
    private void handleRoleChange(HttpSession session, Role newRole) {
        Role oldRole = (Role) session.getAttribute(SESSION_ROLE);
        session.setAttribute(SESSION_ROLE, newRole);

        if (oldRole != null && oldRole != newRole) {
            // SCHWACHSTELLE [BUG_THREAD_STICKINESS, Teil 1/2]: Bei Rollenwechsel werden
            // Session-Daten (Konversationshistorie + gecachter Retrieval-Kontext) NICHT
            // gelöscht. Der Kontext einer vorherigen, höher privilegierten Rolle
            // (z.B. security_analyst) bleibt für die neue Rolle (z.B. employee) erhalten.
            // SOLL: Der else-Zweig muss entfernt werden. SESSION_HISTORY und
            // SESSION_LAST_CONTEXT müssen bei JEDEM Rollenwechsel gelöscht werden.
            if (!bugFlags.threadStickiness()) {
                log.debug("Role changed from {} to {} – clearing session data", oldRole, newRole);
                session.removeAttribute(SESSION_HISTORY);
                session.removeAttribute(SESSION_LAST_CONTEXT);
            } else {
                log.debug("Role changed from {} to {}, session context preserved", oldRole, newRole);
            }
        }
    }

    /**
     * Resolves context for the current request. Reuses recently cached context
     * when available to avoid redundant retrieval operations.
     */
    @SuppressWarnings("unchecked")
    private List<DocumentChunk> resolveContext(Role role, String caseId,
                                              String mode, HttpSession session) {
        // SCHWACHSTELLE [BUG_THREAD_STICKINESS, Teil 2/2]: Gecachter Kontext aus einer
        // vorherigen (höher privilegierten) Session wird in Handover/Workflow-Pfaden
        // wiederverwendet. Analyst → Employee-Wechsel leakt so confidential Chunks.
        // SOLL: Kein Session-Cache nutzen. Immer frisch per
        // retrievalService.retrieve(role, caseId, mode, null) abfragen.
        if (bugFlags.threadStickiness()) {
            List<DocumentChunk> cached =
                    (List<DocumentChunk>) session.getAttribute(SESSION_LAST_CONTEXT);
            if (cached != null && !cached.isEmpty()) {
                log.debug("Reusing {} cached context chunks for {} mode", cached.size(), mode);
                return cached;
            }
        }
        return retrievalService.retrieve(role, caseId, mode, null);
    }

    /**
     * Extracts a suggested workflow action from the LLM response text.
     */
    private String extractSuggestedAction(String reply) {
        if (reply == null) return null;
        String lower = reply.toLowerCase();
        for (String action : List.of("mark_case_likely_false_positive",
                "set_case_priority_low", "route_case_to_finance_queue",
                "attach_supplier_trust_note")) {
            if (lower.contains(action)) {
                return action;
            }
        }
        return null;
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

    private void storeContextInSession(HttpSession session, List<DocumentChunk> context) {
        session.setAttribute(SESSION_LAST_CONTEXT, context);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
