package com.secassist.model;

import java.util.List;

/**
 * Deterministisches Fallbriefing fuer die UI.
 *
 * <p>Das Briefing liefert einen realistischen Incident-Kontext vor dem Chat
 * und stabilisiert dadurch die Exploration. Es ist bewusst knapp gehalten und
 * verraet die vorhandene Schwachstelle nicht.</p>
 *
 * @param caseId              Fall-ID
 * @param title               Anzeigename des Falls
 * @param summary             kompakte Lagebeschreibung
 * @param department          betroffene Abteilung / Einheit
 * @param initialFacts        bekannte Ausgangsfakten
 * @param artifacts           sichtbare, plausible Demo-Artefakte
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