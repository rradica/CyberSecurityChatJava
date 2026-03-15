package com.secassist.model;

/**
 * Request-Objekt für das Hinzufügen einer Fallnotiz.
 *
 * @param text der Notiztext
 */
public record NoteRequest(String text) {}
