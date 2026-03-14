package com.secassist.model;

/**
 * Demo-Rollen für die Workshop-Anwendung.
 *
 * <p>Keine echte Authentisierung – die Rolle wird im UI ausgewählt
 * und mit jedem Request mitgeschickt.</p>
 */
public enum Role {

    /** Externer Auftragnehmer – eingeschränkter Zugriff. */
    CONTRACTOR,

    /** Interner Mitarbeiter – erweiterter Zugriff auf interne Dokumente. */
    EMPLOYEE,

    /** Security-Analyst – voller Zugriff, darf Workflow-Aktionen auslösen. */
    SECURITY_ANALYST;

    /**
     * Parst einen Role-String case-insensitiv.
     *
     * @param value der Rollenname (z.B. "contractor", "SECURITY_ANALYST")
     * @return die entsprechende Rolle
     * @throws IllegalArgumentException bei unbekanntem Wert
     */
    public static Role fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Role must not be empty");
        }
        return valueOf(value.trim().toUpperCase());
    }
}
