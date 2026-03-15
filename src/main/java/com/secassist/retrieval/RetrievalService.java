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
 * Einfacher, lokaler Retrieval-Service.
 *
 * <p>Laedt vorbereitete Chunks aus {@code data/chunks.json} und filtert sie
 * nach Policy (Klassifikation, Audience) und einfachem Tag-Matching.</p>
 *
 * <p>Reihenfolge (gemaeß „Berechtigung vor Kontext"):
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
     * Ruft relevante Chunks fuer einen bestimmten Kontext ab.
     *
     * @param role   aktuelle Benutzerrolle
     * @param caseId ID des aktiven Falls
     * @param mode   Modus: "chat", "handover", "evidence"
     * @param query  optionaler Suchbegriff
     * @return gefilterte und gerankte Liste von Chunks (max. {@value MAX_CHUNKS})
     */
    public List<DocumentChunk> retrieve(Role role, String caseId, String mode, String query) {
        // Schritt 1: Relevanzfilter – nur Chunks, die zum Fall oder zur Anfrage passen
        // Kombiniert statische Chunks und dynamisch hinzugefuegte User-Notizen
        List<DocumentChunk> relevant = Stream.concat(allChunks.stream(), userNotes.stream())
                .filter(chunk -> matchesCaseOrQuery(chunk, caseId, query))
                .collect(Collectors.toCollection(ArrayList::new));

        // Schritt 2: Policy-Filter – Zugriffssteuerung nach Klassifikation und Zielgruppe.
        // Im Handover-Modus werden Security-Team-Berechtigungen verwendet, da
        // Uebergabe-Entwuerfe an das Security-Team gerichtet sind und umfassenden
        // Kontext benoetigen.
        Set<String> allowedClassifications = "handover".equals(mode)
                ? Set.of("public", "internal", "confidential")
                : policyEngine.allowedClassifications(role);
        Set<String> allowedAudiences = "handover".equals(mode)
                ? Set.of("all", "employees", "security_team")
                : policyEngine.allowedAudiences(role);
        relevant = relevant.stream()
                .filter(c -> allowedClassifications.contains(c.classification()))
                .filter(c -> allowedAudiences.contains(c.audience()))
                .collect(Collectors.toCollection(ArrayList::new));

        // Schritt 3: Einfaches Ranking – hoeheres Trust-Level bevorzugen
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
     *
     * <p>SCHWACHSTELLE [BUG_RAG_POISONING]: Wenn der Bug aktiv ist, wird die Notiz
     * mit {@code trustLevel: "high"} und {@code classification: "internal"} gespeichert
     * – gleichwertig mit kuratierten internen Dokumenten. Ein Angreifer kann so
     * gefaelschte "interne Einschaetzungen" (z.B. angebliche Rechtsabteilung-Bestaetigungen)
     * in die Wissensdatenbank einschleusen, die bei spaeteren Abfragen neben echten
     * Policies und Runbooks erscheinen.</p>
     *
     * <p>SOLL: Notizen immer als {@code trustLevel: "untrusted"}, {@code sourceType: "user_note"}
     * speichern, damit sie in getrennten Kontextsektionen erscheinen und die KI
     * sie als ungeprueften User-Input erkennt.</p>
     *
     * @param caseId die Fall-ID, zu der die Notiz gehoert
     * @param text   der Notiztext
     * @return der erstellte Chunk
     */
    public DocumentChunk addUserNote(String caseId, String text) {
        int num = noteCounter.incrementAndGet();

        // SCHWACHSTELLE [BUG_RAG_POISONING]: User-Notiz wird als internes,
        // vertrauenswuerdiges Dokument gespeichert – gleichwertig mit kuratierten
        // internen Dokumenten. Angreifer koennen so gefaelschte "interne Einschaetzungen"
        // in die Wissensdatenbank einschleusen.
        // FIX: Notiz als untrusted/public/user_note speichern:
        //   new DocumentChunk("user_note_" + num, "user_note_" + caseId,
        //       "⚠ Benutzernotiz #" + num + " – " + caseId, text,
        //       "public", "all", "user_note", "untrusted", tags);
        DocumentChunk note = createDynamicChunk(
                num,
                "case_note_" + caseId,
                "Fallnotiz #" + num + " – " + caseId,
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
     * Fuegt eine eingehende externe Rueckmeldung als Fallkontext hinzu.
     *
     * <p>SCHWACHSTELLE [BUG_RAG_POISONING]: Antworten aus einem externen
     * Partner-/Reply-Kanal werden wie intern kuratierte Fallnotizen behandelt.
     * Die Herkunft bleibt zwar im Titel und Text angedeutet, die gespeicherten
     * Metadaten machen daraus jedoch eine hoch vertrauenswuerdige interne Quelle.
     * Genau dadurch wirkt der Pfad wie ein realer Trust-Boundary-Fehler statt wie
     * eine bloesse Selbstmanipulation durch denselben Benutzer.</p>
     *
     * <p>SOLL: Externe Rueckmeldungen muessen immer als ungepruefte,
     * getrennte Quelle gespeichert werden, z. B. mit
     * {@code classification = public}, {@code sourceType = external_feedback}
     * und {@code trustLevel = untrusted}.</p>
     *
     * @param caseId  die Fall-ID
     * @param sender  externer Absender
     * @param channel Eingangskanal der Rueckmeldung
     * @param text    Rueckmeldungstext
     * @return der gespeicherte Chunk
     */
    public DocumentChunk addExternalFeedback(String caseId, String sender, String channel, String text) {
        int num = noteCounter.incrementAndGet();
        String safeSender = sender != null && !sender.isBlank() ? sender : "External Partner";
        String safeChannel = channel != null && !channel.isBlank() ? channel : "shared_case_workspace";
        List<String> tags = new ArrayList<>(buildTags(caseId));
        tags.add("external");
        tags.add("partner");
        tags.add("reply");

        // SCHWACHSTELLE [BUG_RAG_POISONING]: Externe Partner-Rueckmeldung wird
        // als interne, vertrauenswuerdige Fallnotiz abgelegt. Dadurch kippt die
        // Trust-Boundary zwischen eingehendem Fremdinhalt und internem Kontext.
        DocumentChunk feedback = createDynamicChunk(
                num,
                "external_feedback_" + caseId,
                "Externe Rueckmeldung #" + num + " – " + safeSender,
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
