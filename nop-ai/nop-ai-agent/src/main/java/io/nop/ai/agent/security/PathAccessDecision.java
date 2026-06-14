package io.nop.ai.agent.security;

/**
 * Two-valued access decision for per-agent glob path-rules (design §4.3).
 *
 * <p>The path-rule model uses {@code allow | deny} (two-valued), matching the
 * current {@link DefaultPathAccessChecker} deny/allow semantics. Design §4.3's
 * {@code read | read-write | deny} three-level model is deferred as a future
 * refinement tied to tool-kind analysis (L2-13/L2-14).
 */
public enum PathAccessDecision {
    ALLOW,
    DENY;

    /**
     * Parse a case-insensitive string ("allow" / "deny") into a
     * {@link PathAccessDecision}. Defaults to {@link #DENY} for any unrecognized
     * value (fail-closed), matching the {@code agent.xdef} default of
     * {@code access="enum:allow,deny|deny"}.
     *
     * @param text the access string from the path-rule model; may be null
     * @return the parsed decision, never null
     */
    public static PathAccessDecision fromString(String text) {
        if (text == null) {
            return DENY;
        }
        String trimmed = text.trim();
        if ("allow".equalsIgnoreCase(trimmed)) {
            return ALLOW;
        }
        if ("deny".equalsIgnoreCase(trimmed)) {
            return DENY;
        }
        return DENY;
    }
}
