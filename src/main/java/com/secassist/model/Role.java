package com.secassist.model;

/**
 * Demo-Rollen fuer die Workshop-Anwendung.
 *
 * <p>Keine echte Authentisierung – die Rolle wird im UI ausgewaehlt
 * und mit jedem Request mitgeschickt.</p>
 */
public enum Role {

    /** Externer Auftragnehmer – eingeschraenkter Zugriff. */
    CONTRACTOR,

    /** Interner Mitarbeiter – erweiterter Zugriff auf interne Dokumente. */
    EMPLOYEE,

    /** Security-Analyst – voller Zugriff, darf Workflow-Aktionen ausloesen. */
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
