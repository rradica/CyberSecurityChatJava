package com.secassist.policy;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.secassist.model.Role;

/**
 * Deterministische Policy-Engine als zentrale Sicherheitsleitplanke der Anwendung.
 *
 * <p>Die Klasse bildet die Regel "Berechtigung vor Kontext" direkt im Code ab.
 * Bevor Retrieval, Prompting oder Workflow-Ausfuehrung stattfinden, legt sie
 * fest, welche Dokumentklassifikationen, Zielgruppen und Werkzeugpfade fuer
 * eine Rolle ueberhaupt zulaessig sind. Dadurch bleibt die eigentliche
 * Sicherheitsentscheidung jederzeit nachvollziehbar und testbar.</p>
 *
 * <p>Fachlich ist die Engine bewusst einfach gehalten: Sie arbeitet mit klaren,
 * expliziten Mengen und wenigen Rollen. Gerade diese Einfachheit ist fuer den
 * Workshop wichtig, weil Teilnehmerinnen und Teilnehmer die Policy schnell
 * lesen, hinterfragen und gezielt haerten koennen.</p>
 */
@Service
public class PolicyEngine {

    /**
     * Gibt die Dokumentklassifikationen zurueck, die eine Rolle lesen darf.
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
     * Gibt die Zielgruppen zurueck, fuer die eine Rolle Inhalte sehen darf.
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
     * Prueft, ob eine Rolle ein bestimmtes Tool/Aktion nutzen darf.
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
