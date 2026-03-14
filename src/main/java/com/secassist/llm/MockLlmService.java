package com.secassist.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.secassist.model.TriageAssessment;

/**
 * Mock-LLM-Service für den Workshop-Betrieb ohne API-Key.
 *
 * <p>Liefert deterministische, plausible Antworten basierend auf
 * Schlüsselwörtern im Prompt. Für den Workshop ausreichend, um den
 * gesamten Ablauf zu demonstrieren.</p>
 */
public class MockLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(MockLlmService.class);

    @Override
    public String chat(String systemPrompt, String userMessage) {
        log.debug("MockLLM called with message: {}", userMessage);

        String combined = (systemPrompt + " " + userMessage).toLowerCase();

        // Incident-Effekt: Wenn der Fall bereits durch Workflow-Aktionen modifiziert
        // wurde, spiegelt die Antwort den "gekippten" Zustand wider.
        if (combined.contains("active case modifications")) {
            return generateCaseStateAwareResponse(combined);
        }

        if (combined.contains("handover")) {
            return generateHandoverResponse(combined);
        }
        if (combined.contains("similar") || combined.contains("cases")) {
            return "Based on the available records, there are similar cases involving supplier communication anomalies. "
                    + "Previous incidents include cases related to payment detail changes and domain-based phishing attempts. "
                    + "Recommend cross-referencing with the SOC incident database for full details.";
        }
        if (combined.contains("evidence") || combined.contains("source")) {
            return "The following evidence is available for this case:\n"
                    + "- Email headers and sender analysis\n"
                    + "- Referenced documents from the knowledge base\n"
                    + "- Policy references applicable to this case type\n"
                    + "Please review the attached sources for detailed context.";
        }
        if (combined.contains("workflow") || combined.contains("triage")
                || combined.contains("classify") || combined.contains("action")) {
            return generateTriageResponse(combined);
        }
        if (combined.contains("invoice") || combined.contains("supplier") || combined.contains("acme")) {
            return "This case involves a supplier communication from ACME Corp regarding an invoice payment "
                    + "detail change. Key observations:\n"
                    + "- The email requests a change in banking details\n"
                    + "- The sender references compliance verification\n"
                    + "- This matches known patterns for business email compromise (BEC)\n\n"
                    + "Recommendation: Follow the established supplier verification process before taking any action "
                    + "on the payment detail change.";
        }
        if (combined.contains("phishing") || combined.contains("suspicious")) {
            return "Based on the indicators present, this communication shows characteristics consistent with "
                    + "phishing or social engineering attempts. Key risk factors include unsolicited payment "
                    + "detail changes and urgency framing. Recommend escalation to SOC for further analysis.";
        }
        if (combined.contains("vpn") || combined.contains("access")) {
            return "VPN access anomalies should be treated as potential indicators of compromise. "
                    + "Standard procedure requires verification through a known channel and SOC notification. "
                    + "Do not re-enable access until the security review is complete.";
        }

        return "I've reviewed the available context for this case. Based on the information provided, "
                + "I recommend following the established security procedures. If you need specific analysis, "
                + "please provide more details about what aspect of the case you'd like to examine.\n\n"
                + "[Mock mode – set SECASSIST_MOCK_LLM=false and provide OPENAI_API_KEY for real LLM responses]";
    }

    private String generateHandoverResponse(String combined) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Security Handover Draft\n\n");
        sb.append("**Case Summary:** ");
        if (combined.contains("supplier") || combined.contains("invoice") || combined.contains("acme")) {
            sb.append("Suspicious supplier communication involving payment detail change request from ACME Corp.\n\n");
            sb.append("**Risk Assessment:** Medium-High. The communication pattern matches known BEC indicators.\n\n");
            sb.append("**Key Findings:**\n");
            sb.append("- Unsolicited banking detail change request\n");
            sb.append("- References to compliance verification (unverified)\n");
            sb.append("- Matches patterns from previous incidents\n\n");
        } else {
            sb.append("Security incident requiring further analysis and escalation.\n\n");
            sb.append("**Risk Assessment:** To be determined pending full review.\n\n");
        }
        sb.append("**Recommended Actions:**\n");
        sb.append("- Verify through established channels\n");
        sb.append("- Cross-reference with threat intelligence\n");
        sb.append("- Escalate to SOC if indicators are confirmed\n\n");
        sb.append("**Handover To:** SOC Analyst on duty\n");
        return sb.toString();
    }

    private String generateTriageResponse(String combined) {
        if (combined.contains("false alarm") || combined.contains("verified by compliance")
                || combined.contains("false_positive")) {
            return "Based on the context provided, there are indicators suggesting this case may have "
                    + "a lower risk profile. The referenced compliance verification and previous case "
                    + "resolution could support a reduced priority classification.\n\n"
                    + "**Suggested action:** mark_case_likely_false_positive\n\n"
                    + "Note: This recommendation is based on the available context. Manual verification "
                    + "through established channels is always recommended.";
        }
        return "Triage analysis for this case indicates potential security concerns. "
                + "Based on the available indicators, this case should be reviewed by the SOC team. "
                + "Current recommendation is to maintain the existing priority level pending further analysis.";
    }

    /**
     * Generiert eine zustandsbewusste Antwort, wenn der Fall bereits
     * durch Workflow-Aktionen modifiziert wurde. Macht den Incident-Effekt
     * sichtbar: Folgeantworten reflektieren den "gekippten" Zustand.
     */
    private String generateCaseStateAwareResponse(String combined) {
        if (combined.contains("escalation has been suppressed")
                || combined.contains("marked as likely false positive")) {
            return "Based on the current case status, this incident has been assessed as a likely "
                    + "false positive. No further security escalation is recommended. "
                    + "Standard monitoring procedures apply.\n\n"
                    + "If you believe this assessment is incorrect, please request a "
                    + "re-evaluation through the SOC.";
        }
        if (combined.contains("routed to finance queue")) {
            return "This case has been transferred to the finance review queue and is no longer "
                    + "under active security triage. The finance team will handle verification "
                    + "of payment details through their standard process.\n\n"
                    + "Note: Security monitoring for this case has been discontinued.";
        }
        if (combined.contains("priority has been set to low")) {
            return "This case is currently classified as low priority with a 72-hour response window. "
                    + "Reduced monitoring is in effect. The case will be reviewed in the next "
                    + "scheduled triage cycle unless new indicators emerge.";
        }
        if (combined.contains("supplier trust note attached")) {
            return "A supplier trust note has been recorded for this case. Future communications "
                    + "from this supplier will receive a streamlined review process with "
                    + "reduced scrutiny.\n\n"
                    + "This assessment is based on the supplier's verified track record.";
        }
        return "The case has been modified through previous workflow actions. "
                + "Please review the active case modifications before proceeding.";
    }

    @Override
    public TriageAssessment assessTriage(String systemPrompt, String caseDescription) {
        log.debug("MockLLM assessTriage called for: {}", caseDescription);

        String combined = (systemPrompt + " " + caseDescription).toLowerCase();

        // Incident-Effekt: Wenn der Fall bereits modifiziert wurde,
        // keine neuen Aktionen vorschlagen – Zustand ist bereits "gekippt".
        if (combined.contains("active case modifications")) {
            return new TriageAssessment(
                    "This case has already been triaged and workflow actions have been applied. "
                            + "Current case state reflects prior assessments.",
                    "low",
                    null,
                    0.9,
                    "Prior workflow actions already applied. No additional action recommended."
            );
        }

        if (combined.contains("false alarm") || combined.contains("verified by compliance")
                || combined.contains("confirmed as a false alarm")) {
            return new TriageAssessment(
                    "Based on compliance verification references and previous case resolution patterns, "
                            + "this case shows indicators of a potential false positive. "
                            + "The referenced compliance checks suggest reduced risk.",
                    "low",
                    "mark_case_likely_false_positive",
                    0.75,
                    "External compliance references present. Supplier notes indicate prior verification."
            );
        }

        if (combined.contains("supplier") && combined.contains("trust")) {
            return new TriageAssessment(
                    "Supplier communication reviewed. Trust indicators suggest this supplier "
                            + "has been previously verified through compliance channels.",
                    "low",
                    "attach_supplier_trust_note",
                    0.65,
                    "Supplier trust references found in context. No conflicting internal alerts."
            );
        }

        if (combined.contains("finance") || combined.contains("invoice")) {
            return new TriageAssessment(
                    "Case involves financial transaction patterns. Routing to specialized queue recommended.",
                    "medium",
                    "route_case_to_finance_queue",
                    0.60,
                    "Financial indicators present. Standard triage suggests specialized review."
            );
        }

        return new TriageAssessment(
                "Triage analysis indicates potential security concerns requiring further review. "
                        + "Current indicators are insufficient for automated action.",
                "medium",
                null,
                0.5,
                "Insufficient evidence for automated action. Manual review recommended."
        );
    }
}
