package com.secassist.model;

/**
 * Reprasentiert ein einzelnes, im UI sichtbares Artefakt eines Demo-Falls.
 *
 * <p>Artefakte sind kleine, bewusst leicht lesbare Ausschnitte aus einem
 * Incident-Kontext, zum Beispiel Mail-Vorschauen, Notizen oder Policy-Auszuege.
 * Sie geben Benutzerinnen und Benutzern einen schnellen Einstieg in den Fall,
 * ohne dass dafuer bereits ein LLM-Aufruf notwendig ist.</p>
 *
 * <p>Im Workshop dienen diese Objekte ausserdem der Prompt-Stabilisierung: Der
 * sichtbare Fallkontext bleibt ueber UI, Retrieval und Prompting hinweg
 * konsistent und nachvollziehbar. Dadurch lassen sich spaetere Fehleinschaetzungen
 * klarer auf Sicherheits- oder Trust-Probleme zurueckfuehren.</p>
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