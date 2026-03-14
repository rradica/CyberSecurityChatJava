package com.secassist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature-Flags für die absichtlich eingebauten Workshop-Schwachstellen.
 *
 * <p>Jedes Flag aktiviert (default {@code true}) eine bestimmte Schwachstelle.
 * Das Blue Team kann einzelne Flags auf {@code false} setzen, um den
 * zugehörigen Fix zu aktivieren.</p>
 *
 * @param handoverScope   BUG_HANDOVER_SCOPE – Retrieval-Scope im Handover-Modus zu weit
 * @param existenceOracle BUG_EXISTENCE_ORACLE – Similar-Cases verraten interne Vorfälle
 * @param threadStickiness BUG_THREAD_STICKINESS – Session-Kontext überlebt Rollenwechsel
 * @param trustMerge      BUG_TRUST_MERGE – Trusted/Untrusted Quellen werden vermischt
 * @param toolFasttrack   BUG_TOOL_FASTTRACK – Untrusted-Quelle beeinflusst Tool-Freigabe
 */
@ConfigurationProperties(prefix = "secassist.bug-flags")
public record BugFlagsProperties(
        boolean handoverScope,
        boolean existenceOracle,
        boolean threadStickiness,
        boolean trustMerge,
        boolean toolFasttrack
) {
    /** Default-Konstruktor: alle Bugs aktiv. */
    public BugFlagsProperties() {
        this(true, true, true, true, true);
    }
}
