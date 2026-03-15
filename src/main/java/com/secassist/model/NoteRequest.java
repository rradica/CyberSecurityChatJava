package com.secassist.model;

/**
 * Request-Objekt fuer das Hinzufuegen einer Fallnotiz.
 *
 * @param text der Notiztext
 */
public record NoteRequest(String text) {}
