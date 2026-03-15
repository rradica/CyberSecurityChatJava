package com.secassist.model;

import java.util.List;

/**
 * Beschreibt einen Demo-Fall in einer deterministischen, UI-tauglichen Form.
 *
 * <p>Das Briefing wird vor dem eigentlichen Chat angezeigt und liefert einen
 * festen Ausgangskontext mit Zusammenfassung, Fakten, Artefakten und moeglichen
 * Einstiegsfragen. Dadurch wird die Fallbearbeitung fuer Workshop-Teilnehmerinnen
 * und -Teilnehmer reproduzierbar und leicht verstaendlich.</p>
 *
 * <p>Im Gegensatz zu einer frei generierten Einleitung ist das Briefing bewusst
 * stabil und fachlich kuratiert. Es verraet die vorbereiteten Schwachstellen
 * nicht direkt, schafft aber genau den Kontext, in dem diese Schwachstellen bei
 * normaler Nutzung spaeter sichtbar werden.</p>
 *
 * @param caseId               Fall-ID
 * @param title                Anzeigename des Falls
 * @param summary              kompakte Lagebeschreibung
 * @param department           betroffene Abteilung / Einheit
 * @param initialFacts         bekannte Ausgangsfakten
 * @param artifacts            sichtbare, plausible Demo-Artefakte
 * @param recommendedQuestions neutrale, explorative Einstiegsfragen
 */
public record CaseBriefing(
        String caseId,
        String title,
        String summary,
        String department,
        List<String> initialFacts,
        List<CaseArtifact> artifacts,
        List<String> recommendedQuestions
) {}