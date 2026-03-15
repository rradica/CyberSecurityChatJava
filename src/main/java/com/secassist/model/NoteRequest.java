package com.secassist.model;

/**
 * Minimaler Request-Typ fuer das Hinzufuegen einer internen Fallnotiz.
 *
 * <p>Der Record enthaelt bewusst nur den eigentlichen Notiztext. Damit bleibt
 * die API einfach und zeigt gleichzeitig deutlich, wie wenig strukturierter
 * Zusatzkontext noetig ist, um neue Inhalte in den Retrieval-Bestand eines
 * Falls einzuschleusen.</p>
 *
 * <p>Im Workshop hilft diese Einfachheit dabei, den Unterschied zwischen einer
 * praktischen Produktfunktion und einer spaeter problematischen Wissensaufnahme
 * klar zu sehen.</p>
 *
 * @param text der Notiztext
 */
public record NoteRequest(String text) {}
