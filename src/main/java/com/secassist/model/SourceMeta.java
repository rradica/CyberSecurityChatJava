package com.secassist.model;

/**
 * Metadaten einer im Retrieval verwendeten Quelle.
 *
 * <p>Wird im {@link SecurityContext} an das Frontend weitergegeben,
 * damit die Herkunft und Vertrauensstufe jeder Quelle transparent ist.</p>
 *
 * @param docId      Dokument-ID
 * @param title      Titel des Chunks
 * @param trustLevel Vertrauensstufe: high, medium, low, untrusted
 * @param sourceType Quelltyp: policy, runbook, supplier_note, user_note, case_note, …
 */
public record SourceMeta(
        String docId,
        String title,
        String trustLevel,
        String sourceType
) {}
