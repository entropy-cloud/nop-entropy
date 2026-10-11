package io.nop.lint.core.suppress;

import io.nop.lint.core.node.SourceRange;

import java.util.Set;

/**
 * One suppression scope produced by a suppression provider (design 09):
 * the byte range within which diagnostics of the covered rules are not
 * reported, plus the range of the directive (comment token or annotation)
 * that declared it — the position meta-diagnostics such as
 * {@code unused-disable-directive} point at.
 *
 * <p>Monotonic semantics (design 09 §1): a diagnostic whose range overlaps
 * any span covering its rule id is suppressed; spans never cancel each
 * other.</p>
 *
 * @param range          the suppressed byte range (half-open, as
 *                       {@link SourceRange})
 * @param ruleIds        the rule ids this span covers; <b>empty means all
 *                       rules</b> (a bare line-disable directive or an
 *                       {@code @SuppressWarnings("all")})
 * @param directiveRange the byte range of the declaring directive token
 */
public record SuppressionSpan(SourceRange range, Set<String> ruleIds, SourceRange directiveRange) {

    public SuppressionSpan {
        if (ruleIds == null) {
            ruleIds = Set.of();
        }
        ruleIds = Set.copyOf(ruleIds);
    }

    /**
     * A span for every rule ({@code ruleIds} empty).
     */
    public static SuppressionSpan allRules(SourceRange range, SourceRange directiveRange) {
        return new SuppressionSpan(range, Set.of(), directiveRange);
    }

    /**
     * True when this span suppresses diagnostics of {@code ruleId}: either
     * it covers all rules or names {@code ruleId} explicitly.
     */
    public boolean covers(String ruleId) {
        return ruleIds.isEmpty() || ruleIds.contains(ruleId);
    }

    /**
     * Half-open interval overlap: the diagnostic range and the span share at
     * least one byte. Multi-line diagnostics overlap a single-line span when
     * any of their lines intersect the span (design 09 §2.2).
     */
    public boolean overlaps(SourceRange other) {
        return range.startByte() < other.endByte() && other.startByte() < range.endByte();
    }
}
