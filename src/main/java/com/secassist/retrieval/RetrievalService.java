package com.secassist.retrieval;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.secassist.config.BugFlagsProperties;
import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;
import com.secassist.policy.PolicyEngine;

/**
 * Einfacher, lokaler Retrieval-Service.
 *
 * <p>Lädt vorbereitete Chunks aus {@code data/chunks.json} und filtert sie
 * nach Policy (Klassifikation, Audience) und einfachem Tag-Matching.</p>
 *
 * <p>Reihenfolge (gemäß „Berechtigung vor Kontext"):
 * <ol>
 *   <li>Policy-Filter (Klassifikation + Audience)</li>
 *   <li>Fallbezogene Relevanz (Tags)</li>
 *   <li>Einfaches Ranking</li>
 *   <li>Begrenzung auf max. Chunks</li>
 * </ol></p>
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    private static final int MAX_CHUNKS = 5;

    private final PolicyEngine policyEngine;
    private final BugFlagsProperties bugFlags;
    private final ObjectMapper objectMapper;

    private List<DocumentChunk> allChunks = List.of();

    public RetrievalService(PolicyEngine policyEngine,
                            BugFlagsProperties bugFlags,
                            ObjectMapper objectMapper) {
        this.policyEngine = policyEngine;
        this.bugFlags = bugFlags;
        this.objectMapper = objectMapper;
    }

    /** Lädt die Chunks beim Appstart. */
    @PostConstruct
    void loadChunks() {
        try (InputStream is = new ClassPathResource("data/chunks.json").getInputStream()) {
            allChunks = objectMapper.readValue(is, new TypeReference<>() {});
            log.info("Loaded {} document chunks", allChunks.size());
        } catch (IOException e) {
            log.error("Failed to load chunks.json", e);
            allChunks = List.of();
        }
    }

    /**
     * Ruft relevante Chunks für einen bestimmten Kontext ab.
     *
     * @param role   aktuelle Benutzerrolle
     * @param caseId ID des aktiven Falls
     * @param mode   Modus: "chat", "handover", "evidence"
     * @param query  optionaler Suchbegriff
     * @return gefilterte und gerankte Liste von Chunks (max. {@value MAX_CHUNKS})
     */
    public List<DocumentChunk> retrieve(Role role, String caseId, String mode, String query) {
        // Schritt 1: Relevanzfilter – nur Chunks, die zum Fall oder zur Anfrage passen
        List<DocumentChunk> relevant = allChunks.stream()
                .filter(chunk -> matchesCaseOrQuery(chunk, caseId, query))
                .collect(Collectors.toCollection(ArrayList::new));

        // Schritt 2: Policy-Filter
        // SCHWACHSTELLE [BUG_HANDOVER_SCOPE]: Im Handover-Modus wird der komplette
        // Policy-Filter (Übersprungen: Klassifikation + Audience) übersprungen.
        // Dadurch leaken confidential-Dokumente (z.B. Incident-Postmortems) in
        // Handover-Entwürfe auch für Rollen ohne Zugriffsberechtigung.
        // SOLL: Die if-Bedingung für den Handover-Sonderfall komplett entfernen.
        // Der Policy-Filter (allowedClassifications + allowedAudiences) muss IMMER
        // angewendet werden, unabhängig vom Modus.
        if ("handover".equals(mode) && bugFlags.handoverScope()) {
            log.debug("Handover mode: broadened scope for comprehensive handover context");
        } else {
            Set<String> allowedClassifications = policyEngine.allowedClassifications(role);
            Set<String> allowedAudiences = policyEngine.allowedAudiences(role);
            relevant = relevant.stream()
                    .filter(c -> allowedClassifications.contains(c.classification()))
                    .filter(c -> allowedAudiences.contains(c.audience()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        // Schritt 3: Einfaches Ranking – höheres Trust-Level bevorzugen
        relevant.sort(Comparator.comparingInt(this::trustScore).reversed());

        // Schritt 4: Begrenzung
        return relevant.stream().limit(MAX_CHUNKS).toList();
    }

    /** Gibt alle geladenen Chunks zurück (für Tests). */
    public List<DocumentChunk> getAllChunks() {
        return allChunks;
    }

    // --- interne Hilfsmethoden ---

    private boolean matchesCaseOrQuery(DocumentChunk chunk, String caseId, String query) {
        if (caseId != null && !caseId.isBlank()) {
            // Einfaches Tag-Matching basierend auf Schlüsselwörtern aus der Case-ID
            String[] keywords = caseId.split("_");
            for (String kw : keywords) {
                if (chunk.tags().stream().anyMatch(tag -> tag.equalsIgnoreCase(kw))) {
                    return true;
                }
            }
        }
        if (query != null && !query.isBlank()) {
            String lowerQuery = query.toLowerCase();
            return chunk.text().toLowerCase().contains(lowerQuery)
                    || chunk.title().toLowerCase().contains(lowerQuery)
                    || chunk.tags().stream().anyMatch(t -> t.toLowerCase().contains(lowerQuery));
        }
        // Fallback: alle Chunks als potenziell relevant betrachten
        return caseId == null || caseId.isBlank();
    }

    private int trustScore(DocumentChunk chunk) {
        return switch (chunk.trustLevel()) {
            case "high"      -> 4;
            case "medium"    -> 3;
            case "low"       -> 2;
            case "untrusted" -> 1;
            default          -> 0;
        };
    }
}
