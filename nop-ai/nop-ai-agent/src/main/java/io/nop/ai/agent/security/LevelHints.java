package io.nop.ai.agent.security;

import java.util.Objects;

/**
 * Immutable flat value object carrying the auditable boolean hints consumed by
 * {@link ISecurityLevelResolver} when resolving the {@link SecurityLevel} of an
 * action (design §5.1).
 *
 * <p>Each field is an auditable boolean signal about the action under
 * consideration:
 *
 * <table>
 *   <tr><th>Hint</th><th>Meaning</th></tr>
 *   <tr><td>{@code trustedSource}</td><td>Whether the content source is trusted</td></tr>
 *   <tr><td>{@code writesOutsideWorkspace}</td><td>Whether the action writes outside the workspace</td></tr>
 *   <tr><td>{@code crossesTrustBoundary}</td><td>Whether the action crosses a trust boundary</td></tr>
 *   <tr><td>{@code needsNetwork}</td><td>Whether the action needs network access</td></tr>
 *   <tr><td>{@code highImpact}</td><td>Whether the action is a high-impact operation</td></tr>
 * </table>
 *
 * <p>Pure value definition, no behaviour. Forward-compatible with the future
 * dispatch-path consultation (the L2-13 successor) which will populate these
 * hints at runtime via {@link IContentTrustEvaluator} integration, tool-argument
 * analysis, and workspace-boundary checks.
 */
public final class LevelHints {

    private final boolean trustedSource;
    private final boolean writesOutsideWorkspace;
    private final boolean crossesTrustBoundary;
    private final boolean needsNetwork;
    private final boolean highImpact;

    public LevelHints(boolean trustedSource, boolean writesOutsideWorkspace,
                      boolean crossesTrustBoundary, boolean needsNetwork, boolean highImpact) {
        this.trustedSource = trustedSource;
        this.writesOutsideWorkspace = writesOutsideWorkspace;
        this.crossesTrustBoundary = crossesTrustBoundary;
        this.needsNetwork = needsNetwork;
        this.highImpact = highImpact;
    }

    /**
     * Convenience factory for a {@link LevelHints} with all hints set to
     * {@code false}, corresponding to "no risk signals" — the
     * {@link SecurityLevel#STANDARD} baseline. This is the baseline against
     * which the design §5.1 rule table upgrades are evaluated.
     */
    public static LevelHints defaults() {
        return new LevelHints(false, false, false, false, false);
    }

    public boolean isTrustedSource() {
        return trustedSource;
    }

    public boolean isWritesOutsideWorkspace() {
        return writesOutsideWorkspace;
    }

    public boolean isCrossesTrustBoundary() {
        return crossesTrustBoundary;
    }

    public boolean isNeedsNetwork() {
        return needsNetwork;
    }

    public boolean isHighImpact() {
        return highImpact;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LevelHints that = (LevelHints) o;
        return trustedSource == that.trustedSource
                && writesOutsideWorkspace == that.writesOutsideWorkspace
                && crossesTrustBoundary == that.crossesTrustBoundary
                && needsNetwork == that.needsNetwork
                && highImpact == that.highImpact;
    }

    @Override
    public int hashCode() {
        return Objects.hash(trustedSource, writesOutsideWorkspace, crossesTrustBoundary,
                needsNetwork, highImpact);
    }

    @Override
    public String toString() {
        return "LevelHints{" +
                "trustedSource=" + trustedSource +
                ", writesOutsideWorkspace=" + writesOutsideWorkspace +
                ", crossesTrustBoundary=" + crossesTrustBoundary +
                ", needsNetwork=" + needsNetwork +
                ", highImpact=" + highImpact +
                '}';
    }
}
