package com.secassist.model;

import java.util.List;

/**
 * Ein vorbereiteter Dokument-Chunk mit Metadaten.
 *
 * <p>Die Chunks werden beim Appstart aus {@code chunks.json} geladen
 * und bilden die Grundlage für die lokale Retrieval-Logik.</p>
 *
 * @param id             eindeutige Chunk-ID
 * @param docId          ID des Ursprungsdokuments
 * @param title          Chunk-Titel
 * @param text           Textinhalt
 * @param classification Zugriffsstufe: public, internal, confidential
 * @param audience       Zielgruppe: all, employees, security_team
 * @param sourceType     Quellentyp: policy, runbook, supplier_note, postmortem, helpdesk_guide
 * @param trustLevel     Vertrauensstufe: high, medium, low, untrusted
 * @param tags           Schlagwörter für einfaches Matching
 */
public record DocumentChunk(
        String id,
        String docId,
        String title,
        String text,
        String classification,
        String audience,
        String sourceType,
        String trustLevel,
        List<String> tags
) {}
