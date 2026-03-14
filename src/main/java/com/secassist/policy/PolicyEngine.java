package com.secassist.policy;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.secassist.model.Role;

/**
 * Deterministische Policy-Engine – das Herzstück der Sicherheitsarchitektur.
 *
 * <p>Zentrale Regel: <strong>Berechtigung vor Kontext.</strong>
 * Alle Zugriffsentscheidungen werden hier getroffen, bevor Kontext
 * aufgebaut oder Text generiert wird. Das LLM darf <em>nicht</em>
 * über Berechtigungen entscheiden.</p>
 */
@Service
public class PolicyEngine {

    /**
     * Gibt die Dokumentklassifikationen zurück, die eine Rolle lesen darf.
     *
     * @param role die aktuelle Benutzerrolle
     * @return Menge der erlaubten Klassifikationen
     */
    public Set<String> allowedClassifications(Role role) {
        return switch (role) {
            case CONTRACTOR      -> Set.of("public");
            case EMPLOYEE        -> Set.of("public", "internal");
            case SECURITY_ANALYST -> Set.of("public", "internal", "confidential");
        };
    }

    /**
     * Gibt die Zielgruppen zurück, für die eine Rolle Inhalte sehen darf.
     *
     * @param role die aktuelle Benutzerrolle
     * @return Menge der erlaubten Zielgruppen
     */
    public Set<String> allowedAudiences(Role role) {
        return switch (role) {
            case CONTRACTOR      -> Set.of("all");
            case EMPLOYEE        -> Set.of("all", "employees");
            case SECURITY_ANALYST -> Set.of("all", "employees", "security_team");
        };
    }

    /**
     * Prüft, ob eine Rolle ein bestimmtes Tool/Aktion nutzen darf.
     *
     * @param role     die aktuelle Benutzerrolle
     * @param toolName Name des Tools/Aktion
     * @return {@code true}, wenn die Rolle das Tool nutzen darf
     */
    public boolean canUseTool(Role role, String toolName) {
        return switch (toolName) {
            case "create_handover_draft" ->
                    role == Role.SECURITY_ANALYST || role == Role.EMPLOYEE;
            case "show_similar_cases" ->
                    role != Role.CONTRACTOR;
            case "show_evidence" ->
                    true; // alle Rollen, aber Ergebnisse werden nach Klassifikation gefiltert
            case "mark_case_likely_false_positive",
                 "set_case_priority_low",
                 "route_case_to_finance_queue",
                 "attach_supplier_trust_note" ->
                    role == Role.SECURITY_ANALYST;
            default -> false;
        };
    }
}
