package io.nop.lint.core.suppress;

/**
 * The engine-generated suppression meta-diagnostics (design 09 §2.2): fixed
 * rule ids and severities. These are not {@code .rule.yml} rules — they have
 * no loading surface and are never candidates for suppression themselves.
 */
public final class SuppressionMeta {

    /**
     * A disable-type suppression directive whose scope suppressed no
     * diagnostic (design 09 §2.2; warning level).
     */
    public static final String UNUSED_DISABLE_DIRECTIVE = "unused-disable-directive";

    /**
     * A disable/enable pairing violation: a disable still open at end of
     * file, or an enable with no matching open disable (design 09 §2.2;
     * info level).
     */
    public static final String UNPAIRED_DISABLE = "unpaired-disable";

    /**
     * The fixed severity of {@link #UNUSED_DISABLE_DIRECTIVE}.
     */
    public static final String SEVERITY_UNUSED = "warning";

    /**
     * The fixed severity of {@link #UNPAIRED_DISABLE}.
     */
    public static final String SEVERITY_UNPAIRED = "info";

    private SuppressionMeta() {
    }
}
