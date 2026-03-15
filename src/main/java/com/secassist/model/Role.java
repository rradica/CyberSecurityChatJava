package com.secassist.model;

/**
 * Definiert die wenigen, bewusst einfachen Demo-Rollen der Anwendung.
 *
 * <p>SecAssist verzichtet absichtlich auf echte Authentisierung und verwendet
 * stattdessen eine im UI gewaehltete Rolle pro Request. Dadurch bleibt das
 * Rollenmodell im Workshop leicht nachvollziehbar und die Wirkung einzelner
 * Policy-Entscheidungen ist direkt im Code und in der UI sichtbar.</p>
 *
 * <p>Trotz dieser Einfachheit ist der Enum fachlich zentral: Auf ihm bauen
 * Retrieval-Filter, Tool-Freigaben, Similar-Cases-Sichtbarkeit und externe
 * Kollaborationspfade auf. Fehler im Umgang mit der Rolle wirken sich daher
 * direkt auf die gesamte Sicherheitslogik aus.</p>
 */
public enum Role {

    /** Externer Auftragnehmer - eingeschraenkter Zugriff. */
    CONTRACTOR,

    /** Interner Mitarbeiter - erweiterter Zugriff auf interne Dokumente. */
    EMPLOYEE,

    /** Security-Analyst - voller Zugriff, darf Workflow-Aktionen ausloesen. */
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
