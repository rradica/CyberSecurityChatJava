package com.secassist.model;

import java.util.List;

/**
 * Reprasentiert einen einzelnen Retrieval-Chunk mit Fach- und Sicherheitsmetadaten.
 *
 * <p>Chunks sind die kleinste Retrieval-Einheit von SecAssist. Sie enthalten
 * nicht nur den eigentlichen Text, sondern auch Klassifikation, Zielgruppe,
 * Quellentyp, Vertrauensniveau und Tags fuer das einfache Matching. Dadurch
 * kann die Anwendung Dokumente vor dem Prompt-Aufbau deterministisch filtern und
 * einordnen.</p>
 *
 * <p>Gerade fuer den Workshop ist dieser Typ wichtig, weil an ihm die Trust-
 * Boundary sichtbar wird: Ein und derselbe Text kann je nach Metadaten eine
 * kuratierte Richtlinie, eine harmlose Notiz oder ein gefaehrlicher externer
 * Fremdinhalt sein. Diese Unterschiede muessen im Anwendungscode sauber
 * beruecksichtigt werden.</p>
 *
 * @param id             eindeutige Chunk-ID
 * @param docId          ID des Ursprungsdokuments
 * @param title          Chunk-Titel
 * @param text           Textinhalt
 * @param classification Zugriffsstufe: public, internal, confidential
 * @param audience       Zielgruppe: all, employees, security_team
 * @param sourceType     Quellentyp: policy, runbook, supplier_note, postmortem, helpdesk_guide
 * @param trustLevel     Vertrauensstufe: high, medium, low, untrusted
 * @param tags           Schlagwoerter fuer einfaches Matching
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
