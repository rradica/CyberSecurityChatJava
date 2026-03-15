package com.secassist.model;

/**
 * Kleines, UI-taugliches Artefakt eines Demo-Falls.
 *
 * <p>Artefakte sollen den Fall realistischer wirken lassen und dem Benutzer
 * einen plausiblen Einstieg in die Exploration geben, ohne die vorbereitete
 * Schwachstelle direkt zu verraten.</p>
 *
 * @param id      technische ID des Artefakts
 * @param type    Typ des Artefakts (z. B. {@code email}, {@code note}, {@code policy_excerpt})
 * @param title   kurzer Anzeigename
 * @param preview kompakte Vorschau fuer UI und Prompt-Stabilisierung
 */
public record CaseArtifact(
        String id,
        String type,
        String title,
        String preview
) {}