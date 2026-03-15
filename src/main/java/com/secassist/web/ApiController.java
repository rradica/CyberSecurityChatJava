package com.secassist.web;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.secassist.model.ChatRequest;
import com.secassist.model.ChatResponse;
import com.secassist.model.ConversationRequest;
import com.secassist.model.DemoCase;
import com.secassist.model.DocumentChunk;
import com.secassist.model.NoteRequest;
import com.secassist.model.Role;
import com.secassist.retrieval.RetrievalService;
import com.secassist.service.ChatOrchestrator;
import com.secassist.service.ConversationService;
import com.secassist.service.DemoCaseService;

/**
 * REST-API fuer die SecAssist-Anwendung.
 *
 * <p>Stellt alle Endpunkte bereit, die vom statischen Frontend konsumiert werden.</p>
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final ChatOrchestrator orchestrator;
    private final ConversationService conversationService;
    private final DemoCaseService demoCaseService;
    private final RetrievalService retrievalService;

    public ApiController(ChatOrchestrator orchestrator,
                         ConversationService conversationService,
                         DemoCaseService demoCaseService,
                         RetrievalService retrievalService) {
        this.orchestrator = orchestrator;
        this.conversationService = conversationService;
        this.demoCaseService = demoCaseService;
        this.retrievalService = retrievalService;
    }

    /**
     * Hauptendpunkt fuer Chat- und Aktions-Requests.
     *
     * @param request der eingehende Request
     * @param session die HTTP-Session
     * @return die Chatbot-Antwort
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request, HttpSession session) {
        return orchestrator.processRequest(request, session);
    }

    /**
     * Konversationeller Endpunkt – natuerliche Freitext-Eingabe.
     *
     * <p>Akzeptiert nur Rolle und Nachricht. Case und Aktion werden
     * automatisch per LLM-Intent-Erkennung bestimmt.</p>
     *
     * @param request Freitext-Request (role + message)
     * @param session die HTTP-Session
     * @return die Chatbot-Antwort
     */
    @PostMapping("/conversation")
    public ChatResponse conversation(@RequestBody ConversationRequest request, HttpSession session) {
        return conversationService.processMessage(request, session);
    }

    /** Gibt die verfuegbaren Demo-Faelle zurueck. */
    @GetMapping("/cases")
    public List<DemoCase> getCases() {
        return demoCaseService.getPublicCases();
    }

    /** Gibt die verfuegbaren Rollen zurueck. */
    @GetMapping("/roles")
    public List<String> getRoles() {
        return Arrays.stream(Role.values())
                .map(r -> r.name().toLowerCase())
                .toList();
    }

    /**
     * Fuegt eine Benutzernotiz zu einem Fall hinzu.
     *
     * <p>Die Notiz wird als Chunk in die Wissensdatenbank aufgenommen
     * und beeinflusst zukuenftige Retrieval-Ergebnisse fuer diesen Fall.</p>
     *
     * @param caseId die Fall-ID
     * @param request der Notiztext
     * @return der erstellte Chunk
     */
    @PostMapping("/cases/{caseId}/notes")
    public DocumentChunk addNote(@PathVariable String caseId, @RequestBody NoteRequest request) {
        return retrievalService.addUserNote(caseId, request.text());
    }

    /** Entfernt alle dynamisch hinzugefuegten User-Notizen. */
    @DeleteMapping("/notes")
    public Map<String, String> clearNotes() {
        retrievalService.clearUserNotes();
        return Map.of("status", "cleared");
    }

    /** Healthcheck-Endpunkt. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "app", "SecAssist");
    }
}
