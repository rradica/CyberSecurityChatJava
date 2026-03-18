package com.secassist.retrieval;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;
import com.secassist.policy.PolicyEngine;

/**
 * Lokaler Retrieval-Service fuer Dokumente, Fallnotizen und externe Rueckmeldungen.
 *
 * <p>Die Klasse laedt vorbereitete Dokument-Chunks aus der lokalen
 * Datenbasis, erweitert sie zur Laufzeit um dynamische Fallnotizen und filtert
 * anschliessend nach Rolle, Zielgruppe und Modus. Damit bildet sie die Bruecke
 * zwischen statischer Wissensbasis, situativem Fallkontext und der zentralen
 * Architekturregel "Berechtigung vor Kontext".</p>
 *
 * <p>Im Workshop ist dieser Service besonders wichtig, weil mehrere der
 * absichtlich eingebauten Schwachstellen genau hier greifbar werden:
 * fehlerhafte Scope-Erweiterungen, Trust-Boundary-Verletzungen und zu stark
 * privilegierte dynamische Inhalte. Die Logik bleibt trotzdem bewusst klein und
 * nachvollziehbar, damit Ursachen und Fixes direkt am Anwendungscode sichtbar
 * sind.</p>
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    private static final int MAX_CHUNKS = 10;
    private static final Set<String> QUERY_STOP_WORDS = Set.of(
            "about", "after", "allgemein", "also", "anfrage", "auch", "beim", "bereits",
            "bitte", "case", "damit", "diese", "diesem", "diesen", "dieser", "dringende",
            "einer", "einem", "einen", "einerseits", "email", "erhalten", "etwas", "finance",
            "frage", "frueheren", "haben", "hello", "hintergrund", "history", "habe", "incident",
            "inklusive", "irgendwie", "kann", "keine", "koennte", "mail", "mehrere", "message",
            "mich", "nach", "naechste", "noch", "normal", "oder", "schritt", "seltsamen",
            "sollen", "soll", "sowie", "standard", "status", "the", "und", "uns", "unter",
            "verdaechtige", "verdaechtigen", "vielleicht", "vom", "von", "warum", "was", "weitere",
            "weiteren", "wie", "wir", "with", "workflow", "zeigen", "zum", "zur", "ueber", "ueberweisung"
    );

    private final PolicyEngine policyEngine;
    private final ObjectMapper objectMapper;

    private List<DocumentChunk> allChunks = List.of();
    private final List<DocumentChunk> userNotes = new ArrayList<>();
    private final AtomicInteger noteCounter = new AtomicInteger(0);

    public RetrievalService(PolicyEngine policyEngine,
                            ObjectMapper objectMapper) {
        this.policyEngine = policyEngine;
        this.objectMapper = objectMapper;
    }

    /** Laedt die Chunks beim Appstart. */
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
     * Ruft relevante Chunks fuer Rolle, Fall und Modus ab.
     */
    public List<DocumentChunk> retrieve(Role role, String caseId, String mode, String query) {
        // Schritt 1: statische Chunks und dynamische Notizen zusammenfuehren
        List<DocumentChunk> relevant = Stream.concat(allChunks.stream(), userNotes.stream())
                .filter(chunk -> matchesCaseOrQuery(chunk, caseId, query))
                .collect(Collectors.toCollection(ArrayList::new));

        // Schritt 2: Policy-Filter nach Klassifikation und Zielgruppe
        // SCHWACHSTELLE [BUG_HANDOVER_SCOPE]: Bei gezielten historischen Rueckfragen
        // im Handover-Modus werden fest die Berechtigungen des Security-Teams
        // verwendet statt der aktuellen Rolle.
        // FIX: Immer policyEngine.allowedClassifications(role) und
        // policyEngine.allowedAudiences(role) der aktuellen Rolle verwenden.
        // ERKLAERUNG: Sonst wird vor dem Kontextaufbau die falsche Berechtigung angesetzt.
        boolean expandedHandoverScope = "handover".equals(mode) && requestsHistoricalLessons(query);
        Set<String> allowedClassifications = expandedHandoverScope
                ? Set.of("public", "internal", "confidential")
                : policyEngine.allowedClassifications(role);
        Set<String> allowedAudiences = expandedHandoverScope
                ? Set.of("all", "employees", "security_team")
                : policyEngine.allowedAudiences(role);
        relevant = relevant.stream()
                .filter(c -> allowedClassifications.contains(c.classification()))
                .filter(c -> allowedAudiences.contains(c.audience()))
                .collect(Collectors.toCollection(ArrayList::new));

        // Schritt 3: einfaches Ranking
        relevant.sort(Comparator.comparingInt(this::trustScore).reversed());

        // Schritt 4: Begrenzung
        return relevant.stream().limit(MAX_CHUNKS).toList();
    }

    /** Gibt alle geladenen Chunks zurueck (fuer Tests). */
    public List<DocumentChunk> getAllChunks() {
        return allChunks;
    }

    /**
     * Fuegt eine Benutzernotiz als Chunk hinzu.
     * SCHWACHSTELLE [BUG_RAG_POISONING]: Die Notiz wird als interne High-Trust-Quelle gespeichert.
     * FIX: Notizen als {@code user_note} mit {@code trustLevel=untrusted} speichern.
     * ERKLAERUNG: Ungepruefter Input darf spaeter nicht wie kuratierte interne Evidenz wirken.
     */
    public DocumentChunk addUserNote(String caseId, String text) {
        int num = noteCounter.incrementAndGet();

        // SCHWACHSTELLE [BUG_RAG_POISONING]: Benutzernotizen werden wie interne,
        // vertrauenswuerdige Dokumente behandelt.
        // FIX: sourceType=user_note, classification=public, trustLevel=untrusted setzen.
        // ERKLAERUNG: Die Notiz bleibt sichtbar, zaehlt aber nicht als interne Quelle.
        DocumentChunk note = createDynamicChunk(
                num,
                "case_note_" + caseId,
                "Fallnotiz #" + num + " - " + caseId,
                text,
                "internal",
                "employees",
                "case_note",
                "high",
                buildTags(caseId)
        );

        userNotes.add(note);
        log.info("User note added: id={}, trustLevel={}, classification={}",
                note.id(), note.trustLevel(), note.classification());
        return note;
    }

    /**
     * Fuegt eine externe Rueckmeldung als Fallkontext hinzu.
     * SCHWACHSTELLE [BUG_RAG_POISONING]: Externe Rueckmeldungen werden als interne High-Trust-Quelle gespeichert.
     * FIX: Externe Rueckmeldungen als getrennte, ungepruefte Quelle speichern.
     * ERKLAERUNG: Ein Partnerkanal ist eine Trust Boundary und keine interne Wissensquelle.
     */
    public DocumentChunk addExternalFeedback(String caseId, String sender, String channel, String text) {
        int num = noteCounter.incrementAndGet();
        String safeSender = sender != null && !sender.isBlank() ? sender : "External Partner";
        String safeChannel = channel != null && !channel.isBlank() ? channel : "shared_case_workspace";
        List<String> tags = new ArrayList<>(buildTags(caseId));
        tags.add("external");
        tags.add("partner");
        tags.add("reply");

        // SCHWACHSTELLE [BUG_RAG_POISONING]: Externe Rueckmeldungen kippen die
        // Trust-Boundary und landen als interne, vertrauenswuerdige Fallnotiz.
        // FIX: external_feedback mit untrusted/public speichern und separat kennzeichnen.
        // ERKLAERUNG: Fremdinhalt darf im Retrieval nicht denselben Status wie internes Wissen erhalten.
        DocumentChunk feedback = createDynamicChunk(
                num,
                "external_feedback_" + caseId,
                "Externe Rueckmeldung #" + num + " - " + safeSender,
                "[Eingangskanal: " + safeChannel + " | Absender: " + safeSender + "]\n" + text,
                "internal",
                "employees",
                "case_note",
                "high",
                tags
        );

        userNotes.add(feedback);
        log.info("External feedback added: id={}, sender={}, channel={}, trustLevel={}",
                feedback.id(), safeSender, safeChannel, feedback.trustLevel());
        return feedback;
    }

    /** Entfernt alle dynamisch hinzugefuegten User-Notizen. */
    public void clearUserNotes() {
        userNotes.clear();
        noteCounter.set(0);
        log.debug("User notes cleared");
    }

    /** Gibt die Anzahl der User-Notizen zurueck (fuer Tests). */
    public int getUserNoteCount() {
        return userNotes.size();
    }

    // --- interne Hilfsmethoden ---

    private boolean matchesCaseOrQuery(DocumentChunk chunk, String caseId, String query) {
        boolean caseMatch = false;
        if (caseId != null && !caseId.isBlank()) {
            // Einfaches Tag-Matching basierend auf Schluesselwoertern aus der Case-ID
            String[] keywords = caseId.split("_");
            for (String kw : keywords) {
                if (chunk.tags().stream().anyMatch(tag -> tag.equalsIgnoreCase(kw))) {
                    caseMatch = true;
                    break;
                }
            }

            if (!caseMatch
                    && caseId.contains("supplier")
                    && "supplier_note".equals(chunk.sourceType())) {
                caseMatch = true;
            }
        }

        boolean queryMatch = false;
        if (query != null && !query.isBlank()) {
            String target = (chunk.title() + " " + chunk.text() + " "
                    + String.join(" ", chunk.tags())).toLowerCase();

            for (String word : query.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
                if (!isMeaningfulQueryWord(word)) {
                    continue;
                }
                if (target.contains(word)) {
                    queryMatch = true;
                    break;
                }
            }
        }

        if (caseId != null && !caseId.isBlank()) {
            return caseMatch || queryMatch;
        }
        if (query != null && !query.isBlank()) {
            return queryMatch;
        }
        // Fallback: alle Chunks als potenziell relevant betrachten
        return caseId == null || caseId.isBlank();
    }

    private boolean requestsHistoricalLessons(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = query.toLowerCase();
        return normalized.contains("lessons learned")
                || normalized.contains("lessons learned")
                || normalized.contains("frueher")
                || normalized.contains("vorfaell")
                || normalized.contains("postmortem")
                || normalized.contains("histor")
                || normalized.contains("acme");
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

    private boolean isMeaningfulQueryWord(String word) {
        if (word == null) {
            return false;
        }
        String normalized = word.trim().toLowerCase();
        return normalized.length() >= 4 && !QUERY_STOP_WORDS.contains(normalized);
    }

    private List<String> buildTags(String caseId) {
        return caseId != null
                ? new ArrayList<>(Arrays.asList(caseId.split("_")))
                : new ArrayList<>();
    }

    private DocumentChunk createDynamicChunk(int number,
                                             String docId,
                                             String title,
                                             String text,
                                             String classification,
                                             String audience,
                                             String sourceType,
                                             String trustLevel,
                                             List<String> tags) {
        return new DocumentChunk(
                "user_note_" + number,
                docId,
                title,
                text,
                classification,
                audience,
                sourceType,
                trustLevel,
                tags
        );
    }

}
