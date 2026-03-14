package com.secassist.web;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.secassist.config.BugFlagsProperties;
import com.secassist.model.ChatRequest;
import com.secassist.model.ChatResponse;
import com.secassist.model.DemoCase;
import com.secassist.model.Role;
import com.secassist.service.ChatOrchestrator;
import com.secassist.service.DemoCaseService;

/**
 * REST-API für die SecAssist-Anwendung.
 *
 * <p>Stellt alle Endpunkte bereit, die vom statischen Frontend konsumiert werden.</p>
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final ChatOrchestrator orchestrator;
    private final DemoCaseService demoCaseService;
    private final BugFlagsProperties bugFlags;

    public ApiController(ChatOrchestrator orchestrator,
                         DemoCaseService demoCaseService,
                         BugFlagsProperties bugFlags) {
        this.orchestrator = orchestrator;
        this.demoCaseService = demoCaseService;
        this.bugFlags = bugFlags;
    }

    /**
     * Hauptendpunkt für Chat- und Aktions-Requests.
     *
     * @param request der eingehende Request
     * @param session die HTTP-Session
     * @return die Chatbot-Antwort
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request, HttpSession session) {
        return orchestrator.processRequest(request, session);
    }

    /** Gibt die verfügbaren Demo-Fälle zurück. */
    @GetMapping("/cases")
    public List<DemoCase> getCases() {
        return demoCaseService.getPublicCases();
    }

    /** Gibt die verfügbaren Rollen zurück. */
    @GetMapping("/roles")
    public List<String> getRoles() {
        return Arrays.stream(Role.values())
                .map(r -> r.name().toLowerCase())
                .toList();
    }

    /** Gibt die aktuellen Bug-Flag-Einstellungen zurück. */
    @GetMapping("/bug-flags")
    public BugFlagsProperties getBugFlags() {
        return bugFlags;
    }

    /** Healthcheck-Endpunkt. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "app", "SecAssist");
    }
}
