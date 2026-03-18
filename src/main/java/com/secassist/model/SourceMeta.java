package com.secassist.model;

/**
 * Kompakte Metadatenansicht einer beim Retrieval verwendeten Quelle.
 *
 * <p>Der Record reduziert einen {@link DocumentChunk} auf genau die Angaben,
 * die fuer Transparenz im Frontend und in Tests relevant sind: Dokumentbezug,
 * sichtbarer Titel, Vertrauensniveau, Quelltyp und Klassifizierung. So bleibt
 * nachvollziehbar, welche Art von Material in eine Antwort eingeflossen ist.</p>
 *
 * <p>Diese reduzierte Darstellung ist bewusst von den eigentlichen Chunk-Texten
 * getrennt. Dadurch koennen Benutzerinnen und Benutzer sowie Workshop-Teams die
 * Sicherheitsherkunft einer Quelle beurteilen, ohne zwangslaeufig den gesamten
 * Inhalt erneut anzeigen zu muessen.</p>
 *
 * @param docId          Dokument-ID
 * @param title          Titel des Chunks
 * @param trustLevel     Vertrauensstufe: high, medium, low, untrusted
 * @param sourceType     Quelltyp: policy, runbook, supplier_note, user_note, case_note, ...
 * @param classification Klassifizierung: public, internal, confidential
 * @param origin         fachliche Herkunft, z. B. internal_document, supplier_input, external_feedback
 * @param includedBy     warum die Quelle in den aktuellen Kontext gelangte
 * @param influence      grobe Einordnung ihres Gewichts im sichtbaren Arbeitskontext
 */
public record SourceMeta(
        String docId,
        String title,
        String trustLevel,
        String sourceType,
        String classification,
        String origin,
        String includedBy,
        String influence
) {}
