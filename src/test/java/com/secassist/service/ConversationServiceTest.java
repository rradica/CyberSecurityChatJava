package com.secassist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.secassist.llm.LlmService;
import com.secassist.model.ChatRequest;
import com.secassist.model.ChatResponse;
import com.secassist.model.ConversationIntent;
import com.secassist.model.ConversationRequest;
import com.secassist.model.DemoCase;
import com.secassist.model.DocumentChunk;
import com.secassist.model.SecurityContext;
import com.secassist.retrieval.RetrievalService;

class ConversationServiceTest {

    private LlmService llmService;
    private ChatOrchestrator orchestrator;
    private DemoCaseService demoCaseService;
    private RetrievalService retrievalService;
    private HttpSession session;
    private ConversationService service;

    private static final List<DemoCase> CASES = List.of(
            new DemoCase("suspicious_supplier_invoice",
                    "Suspicious Supplier Invoice",
                    "ACME Corp invoice with changed banking details",
                    "phishing", "high", List.of("doc1")),
            new DemoCase("vpn_password_reset",
                    "VPN Password Reset from Unknown Location",
                    "Password reset request from unusual IP",
                    "access_control", "medium", List.of("doc2"))
    );

    @BeforeEach
    void setUp() {
        llmService = mock(LlmService.class);
        orchestrator = mock(ChatOrchestrator.class);
        demoCaseService = mock(DemoCaseService.class);
        retrievalService = mock(RetrievalService.class);
        session = mock(HttpSession.class);

        when(demoCaseService.getPublicCases()).thenReturn(CASES);
        when(orchestrator.processRequest(any(ChatRequest.class), any(HttpSession.class)))
                .thenReturn(ChatResponse.text("Mock response"));

        service = new ConversationService(llmService, orchestrator, demoCaseService, retrievalService);
    }

    @Test
    void blankMessageReturnsHelpText() {
        var req = new ConversationRequest("employee", "   ");
        var response = service.processMessage(req, session);

        assertThat(response.reply()).containsIgnoringCase("Sicherheitsvorfall");
    }

    @Test
    void nullMessageReturnsHelpText() {
        var req = new ConversationRequest("employee", null);
        var response = service.processMessage(req, session);

        assertThat(response.reply()).containsIgnoringCase("Sicherheitsvorfall");
    }

    @Test
    void detectedCaseIsPassedToOrchestrator() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(
                        "suspicious_supplier_invoice", "chat", "test"));

        var req = new ConversationRequest("employee", "Tell me about the ACME invoice");
        service.processMessage(req, session);

        var captor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(orchestrator).processRequest(captor.capture(), any());
        assertThat(captor.getValue().caseId()).isEqualTo("suspicious_supplier_invoice");
        assertThat(captor.getValue().action()).isEqualTo("chat");
        assertThat(captor.getValue().role()).isEqualTo("employee");
    }

    @Test
    void triageIntentMapsToWorkflowAction() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(
                        "suspicious_supplier_invoice", "triage", "test"));

        var req = new ConversationRequest("security_analyst", "Assess this case");
        service.processMessage(req, session);

        var captor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(orchestrator).processRequest(captor.capture(), any());
        assertThat(captor.getValue().action()).isEqualTo("workflow");
        assertThat(captor.getValue().message()).isEqualTo("Assess this case");
    }

    @Test
    void handoverIntentMapsToHandoverAction() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(
                        "vpn_password_reset", "handover", "test"));

        var req = new ConversationRequest("employee", "Create a handover draft");
        service.processMessage(req, session);

        var captor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(orchestrator).processRequest(captor.capture(), any());
        assertThat(captor.getValue().action()).isEqualTo("handover");
    }

    @Test
    void similarCasesIntentMapsCorrectly() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(
                        "suspicious_supplier_invoice", "similar_cases", "test"));

        var req = new ConversationRequest("employee", "Were there similar cases?");
        service.processMessage(req, session);

        var captor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(orchestrator).processRequest(captor.capture(), any());
        assertThat(captor.getValue().action()).isEqualTo("similar_cases");
        // Nachricht wird durchgereicht f\u00fcr erweiterte \u00c4hnlichkeitssuche
        assertThat(captor.getValue().message()).isEqualTo("Were there similar cases?");
    }

    @Test
    void evidenceIntentMapsCorrectly() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(
                        "vpn_password_reset", "evidence", "test"));

        var req = new ConversationRequest("security_analyst", "Show me the evidence");
        service.processMessage(req, session);

        var captor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(orchestrator).processRequest(captor.capture(), any());
        assertThat(captor.getValue().action()).isEqualTo("evidence");
    }

    @Test
    void sessionFallbackUsedWhenNoCaseDetected() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(null, "chat", "no case"));
        when(session.getAttribute("activeCase"))
                .thenReturn("vpn_password_reset");

        var req = new ConversationRequest("employee", "Tell me more");
        service.processMessage(req, session);

        var captor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(orchestrator).processRequest(captor.capture(), any());
        assertThat(captor.getValue().caseId()).isEqualTo("vpn_password_reset");
    }

    @Test
    void noCaseAndNoSessionReturnsGuidance() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(null, "chat", "no case"));
        when(session.getAttribute("activeCase")).thenReturn(null);

        var req = new ConversationRequest("employee", "Hello, I need help");
        var response = service.processMessage(req, session);

        assertThat(response.reply()).contains("Sicherheitsvorfall");
    }

    @Test
    void detectedCaseStoredInSession() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(
                        "suspicious_supplier_invoice", "chat", "test"));

        var req = new ConversationRequest("employee", "ACME invoice issue");
        service.processMessage(req, session);

        verify(session).setAttribute("activeCase", "suspicious_supplier_invoice");
    }

    @Test
    void unknownIntentSanitizedToChat() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(
                        "vpn_password_reset", "unknown_garbage", "test"));

        var req = new ConversationRequest("employee", "Something about VPN");
        service.processMessage(req, session);

        var captor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(orchestrator).processRequest(captor.capture(), any());
        // sanitized() converts unknown intents to "chat"
        assertThat(captor.getValue().action()).isEqualTo("chat");
    }

    @Test
    void addNoteIntentCallsRetrievalServiceDirectly() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(
                        "suspicious_supplier_invoice", "add_note", "user wants to add note"));

        var noteChunk = new DocumentChunk(
                "user_note_1", "user_note_suspicious_supplier_invoice",
                "Benutzernotiz #1", "Test note text",
                "public", "all", "user_note", "untrusted", List.of());
        when(retrievalService.addUserNote(eq("suspicious_supplier_invoice"), anyString()))
                .thenReturn(noteChunk);

        var req = new ConversationRequest("employee", "Notiz: Der Lieferant wurde verifiziert");
        var response = service.processMessage(req, session);

        // RetrievalService wurde aufgerufen
        verify(retrievalService).addUserNote(
                eq("suspicious_supplier_invoice"),
                eq("Notiz: Der Lieferant wurde verifiziert"));

        // Orchestrator wurde NICHT aufgerufen
        verify(orchestrator, never()).processRequest(any(), any());

        // Antwort enthaelt Bestaetigung mit Vertrauensstufe
        assertThat(response.reply()).contains("Notiz zu");
        assertThat(response.reply()).contains("suspicious_supplier_invoice");
        assertThat(response.reply()).contains("gespeichert");
        assertThat(response.reply()).contains("Vertrauensstufe");

        // Kein SecurityContext fuer Notiz-Antworten (reduzierte UI-Komplexitaet)
        assertThat(response.securityContext()).isNull();
    }

    @Test
    void explicitCaseIdFromUiOverridesDetectedCase() {
        when(llmService.detectIntent(anyString(), any()))
                .thenReturn(new ConversationIntent(
                        "vpn_password_reset", "chat", "detected wrong case"));

        var req = new ConversationRequest("employee", "Bitte analysiere den Fall", "suspicious_supplier_invoice");
        service.processMessage(req, session);

        var captor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(orchestrator).processRequest(captor.capture(), any());
        assertThat(captor.getValue().caseId()).isEqualTo("suspicious_supplier_invoice");
    }
}
