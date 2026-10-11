package io.nop.lint.core.suppress;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.SourceRange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The suppression pass of the lint pipeline (design 03 §1.1: after xscript,
 * before the diagnostics are emitted; design 09 §1 monotonic semantics).
 * Composes the always-on inline-comment scan with the language's optional
 * annotation provider, filters the candidate diagnostics — one effective
 * suppression is enough, and a suppressed diagnostic produces no fix — and
 * generates the meta-diagnostics: pairing violations from the scan plus
 * {@code unused-disable-directive} for every disable-type directive whose
 * scope suppressed nothing (design 09 §2.2; the check covers inline comments
 * and {@code @SuppressWarnings} spans alike, design 09 §1).
 *
 * <p>Meta-diagnostics are engine-generated fixed ids, never candidates for
 * suppression themselves. Every candidate is judged independently, so an
 * xscript rule reporting the same node twice is suppressed per diagnostic
 * (design 09 §6).</p>
 */
public final class SuppressionFilter {

    private final SuppressionProvider languageProvider;
    private final CommentSuppressionScanner scanner = new CommentSuppressionScanner();

    /**
     * A filter over inline-comment suppression only (languages without an
     * annotation carrier).
     */
    public SuppressionFilter() {
        this(null);
    }

    /**
     * @param languageProvider the language's annotation suppression
     *                         extractor, or null when the language declares
     *                         none
     */
    public SuppressionFilter(SuppressionProvider languageProvider) {
        this.languageProvider = languageProvider;
    }

    /**
     * Judges {@code candidates} against the suppression spans of
     * {@code tree} and produces the emitted diagnostics.
     */
    public SuppressionOutcome evaluate(LintTree tree, List<Diagnostic> candidates) {
        Objects.requireNonNull(tree, "tree must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");

        SuppressionScan scan = scanner.scan(tree);
        List<SuppressionSpan> spans = new ArrayList<>(scan.spans());
        if (languageProvider != null) {
            spans.addAll(languageProvider.extractSpans(tree));
        }

        List<Diagnostic> kept = new ArrayList<>(candidates.size());
        List<Diagnostic> suppressed = new ArrayList<>(candidates.size());
        boolean[] spanUsed = new boolean[spans.size()];
        for (Diagnostic candidate : candidates) {
            boolean blocked = false;
            for (int i = 0; i < spans.size(); i++) {
                SuppressionSpan span = spans.get(i);
                if (span.covers(candidate.ruleId()) && span.overlaps(candidate.range())) {
                    blocked = true;
                    spanUsed[i] = true;
                }
            }
            if (blocked) {
                suppressed.add(candidate);
            } else {
                kept.add(candidate);
            }
        }

        List<Diagnostic> meta = new ArrayList<>(scan.metaDiagnostics());
        meta.addAll(unusedDirectiveMeta(spans, spanUsed, scan.disableDirectiveRanges()));

        List<Diagnostic> emitted = new ArrayList<>(kept.size() + meta.size());
        emitted.addAll(kept);
        emitted.addAll(meta);
        return new SuppressionOutcome(emitted, suppressed, meta);
    }

    /**
     * One {@code unused-disable-directive} per disable-type directive whose
     * spans all suppressed nothing. Directives are identified by their token
     * range ({@link SourceRange} is a value record); annotation spans, which
     * arrive without a comment directive, contribute their own ranges — so
     * the unused check covers both suppression layers. A disable directive
     * that opened no span (a redundant re-disable) has no spans and reports
     * unused at its own token.
     */
    private static List<Diagnostic> unusedDirectiveMeta(List<SuppressionSpan> spans, boolean[] spanUsed,
                                                        List<SourceRange> disableDirectives) {
        Map<SourceRange, List<Integer>> directives = new LinkedHashMap<>();
        for (SourceRange range : disableDirectives) {
            directives.computeIfAbsent(range, key -> new ArrayList<>());
        }
        for (int i = 0; i < spans.size(); i++) {
            directives.computeIfAbsent(spans.get(i).directiveRange(), key -> new ArrayList<>()).add(i);
        }

        List<Diagnostic> meta = new ArrayList<>();
        for (Map.Entry<SourceRange, List<Integer>> entry : directives.entrySet()) {
            boolean anyUsed = false;
            for (int index : entry.getValue()) {
                if (spanUsed[index]) {
                    anyUsed = true;
                }
            }
            if (!anyUsed) {
                SourceRange at = entry.getValue().isEmpty()
                        ? entry.getKey()
                        : spans.get(entry.getValue().get(0)).directiveRange();
                meta.add(new Diagnostic(SuppressionMeta.UNUSED_DISABLE_DIRECTIVE,
                        SuppressionMeta.SEVERITY_UNUSED,
                        "suppression directive suppresses nothing: no diagnostic matched its scope",
                        at));
            }
        }
        return meta;
    }
}
