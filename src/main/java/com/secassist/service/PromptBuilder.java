package com.secassist.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.secassist.config.BugFlagsProperties;
import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;

/**
 * Baut Prompts aus Kontext und Benutzeranfrage zusammen.
 *
 * <p>Der Context-Aufbau variiert je nach Konfiguration: entweder werden alle
 * Quellen in einer konsolidierten Ansicht zusammengefasst (reduziert kognitiven
 * Aufwand) oder nach Herkunft/Trust-Level getrennt dargestellt.</p>
 */
@Service
public class PromptBuilder {

    private static final String SYSTEM_PREAMBLE =
            "You are SecAssist, a security incident triage assistant. "
            + "You help analyze security-relevant cases and provide recommendations. "
            + "Be factual, concise, and always recommend verification through established channels. "
            + "Never make security decisions autonomously – only provide analysis and suggestions.";

    private final BugFlagsProperties bugFlags;

    public PromptBuilder(BugFlagsProperties bugFlags) {
        this.bugFlags = bugFlags;
    }

    /**
     * Baut den System-Prompt für einen Chat-Request.
     *
     * @param role        aktuelle Benutzerrolle
     * @param caseDesc    Fallbeschreibung
     * @param context     Retrieval-Kontext
     * @param mode        Modus (chat, handover, evidence, workflow)
     * @return zusammengebauter System-Prompt
     */
    public String buildSystemPrompt(Role role, String caseDesc,
                                    List<DocumentChunk> context, String mode) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PREAMBLE).append("\n\n");
        sb.append("Current user role: ").append(role.name()).append("\n");
        sb.append("Current case: ").append(caseDesc).append("\n");
        sb.append("Mode: ").append(mode).append("\n\n");

        if (!context.isEmpty()) {
            // SCHWACHSTELLE [BUG_TRUST_MERGE]: Alle Quellen (interne Policies UND
            // untrusted Supplier-Notes) werden in einen einzigen "Consolidated Guidance"-
            // Block gemischt – ohne jede Herkunftsangabe. Das LLM kann nicht erkennen,
            // ob eine Aussage (z.B. "verified by compliance") aus einer internen Policy
            // oder einer nicht vertrauenswürdigen externen Quelle stammt.
            // SOLL: Kontext nach Trust-Level in getrennte Sektionen aufteilen
            // (vgl. appendSeparatedSections): "Internal Policies & Verified Sources"
            // vs. "External / Unverified Sources" mit klaren [TRUST: sourceType]-Labels.
            if (bugFlags.trustMerge()) {
                appendConsolidatedGuidance(sb, context);
            } else {
                // Separated view: distinct sections by source provenance
                appendSeparatedSections(sb, context);
            }
        }

        if ("handover".equals(mode)) {
            sb.append("Generate a security handover draft for this case. Include risk assessment, "
                    + "key findings, and recommended next steps.\n");
        } else if ("workflow".equals(mode)) {
            sb.append("Analyze this case for triage. If the evidence supports it, suggest "
                    + "an appropriate workflow action (e.g., mark_case_likely_false_positive, "
                    + "set_case_priority_low, route_case_to_finance_queue).\n");
        }

        return sb.toString();
    }

    /**
     * Presents all context in a single consolidated guidance block.
     * Reduces cognitive load by avoiding redundant cross-referencing.
     */
    private void appendConsolidatedGuidance(StringBuilder sb, List<DocumentChunk> context) {
        sb.append("=== Consolidated Case Guidance ===\n");
        sb.append("The following guidance has been compiled from all available sources.\n\n");
        for (DocumentChunk chunk : context) {
            sb.append("• ").append(chunk.title()).append("\n");
            sb.append(chunk.text()).append("\n\n");
        }
    }

    /**
     * Presents context separated by source provenance with clear trust indicators.
     */
    private void appendSeparatedSections(StringBuilder sb, List<DocumentChunk> context) {
        var trusted = context.stream()
                .filter(c -> "high".equals(c.trustLevel()) || "medium".equals(c.trustLevel()))
                .toList();
        var untrusted = context.stream()
                .filter(c -> !"high".equals(c.trustLevel()) && !"medium".equals(c.trustLevel()))
                .toList();

        if (!trusted.isEmpty()) {
            sb.append("=== Internal Policies & Verified Sources ===\n\n");
            for (DocumentChunk chunk : trusted) {
                sb.append("--- [").append(chunk.trustLevel().toUpperCase())
                  .append(": ").append(chunk.sourceType())
                  .append("] ").append(chunk.title()).append(" ---\n");
                sb.append(chunk.text()).append("\n\n");
            }
        }

        if (!untrusted.isEmpty()) {
            sb.append("=== External / Unverified Sources (review critically) ===\n\n");
            for (DocumentChunk chunk : untrusted) {
                sb.append("--- [\u26A0 ").append(chunk.trustLevel().toUpperCase())
                  .append(": ").append(chunk.sourceType())
                  .append("] ").append(chunk.title()).append(" ---\n");
                sb.append(chunk.text()).append("\n\n");
            }
        }
    }

    /**
     * Gibt die Quellen-IDs aus dem Kontext zurück.
     *
     * @param context die verwendeten Chunks
     * @return Liste der Chunk-IDs
     */
    public List<String> extractSourceIds(List<DocumentChunk> context) {
        return context.stream().map(DocumentChunk::id).toList();
    }
}
