package io.nop.lint.core.suppress;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.testing.JavaBindingTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The inline-comment suppression matrix (design 09 §2): every directive
 * form × hit/miss, multi-line diagnostic overlap, enable recovery, EOF
 * unpaired, unused directives, both comment carriers, and the fail-closed
 * unknown-form paths.
 */
class TestCommentSuppression {

    private static final LintLanguage JAVA = JavaBindingTestSupport.javaBinding();

    private static final String R1 = "demo/rule-one";
    private static final String R2 = "demo/rule-two";

    // ==================== helpers ====================

    private static LintTree parse(String source) {
        return JAVA.parse(source);
    }

    private static SuppressionScan scan(String source) {
        return new CommentSuppressionScanner().scan(parse(source));
    }

    private static SuppressionOutcome evaluate(String source, Diagnostic... candidates) {
        return new SuppressionFilter().evaluate(parse(source), List.of(candidates));
    }

    private static Diagnostic at(LintTree tree, String snippet) {
        return diagnostic(R1, rangeOf(tree, snippet));
    }

    private static Diagnostic diagnostic(String ruleId, SourceRange range) {
        return new Diagnostic(ruleId, "error", "message of " + ruleId, range);
    }

    private static SourceRange rangeOf(LintTree tree, String snippet) {
        byte[] source = tree.source();
        byte[] needle = snippet.getBytes(StandardCharsets.UTF_8);
        int index = indexOf(source, needle);
        if (index < 0) {
            throw new IllegalArgumentException("snippet not in source: " + snippet);
        }
        return new SourceRange(index, index + needle.length);
    }

    private static int indexOf(byte[] source, byte[] needle) {
        outer:
        for (int i = 0; i <= source.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (source[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int spanCount(SuppressionScan scan) {
        return scan.spans().size();
    }

    private static List<String> metaIds(SuppressionOutcome outcome) {
        return outcome.metaDiagnostics().stream().map(Diagnostic::ruleId).toList();
    }

    // ==================== disable-next-line ====================

    @Test
    void disableNextLineSuppressesExactlyTheFollowingLine() {
        String source = """
                // nop-lint-disable-next-line demo/rule-one
                int one = violation();
                int two = violation();
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source,
                diagnostic(R1, rangeOf(tree, "int one = violation()")),
                diagnostic(R1, rangeOf(tree, "int two = violation()")));
        assertEquals(1, outcome.suppressed().size(), "only line 2 is in scope");
        assertEquals("int two = violation()", textOf(tree, outcome.diagnostics().get(0).range()));
        assertEquals(0, outcome.metaDiagnostics().size(), "the directive was used");
    }

    @Test
    void disableNextLineWithoutRulesCoversEveryRule() {
        String source = """
                // nop-lint-disable-next-line
                int one = violation();
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source,
                at(tree, "violation()"),
                diagnostic(R2, rangeOf(tree, "violation()")));
        assertEquals(2, outcome.suppressed().size(), "all rules suppressed on the next line");
        assertTrue(outcome.diagnostics().isEmpty());
    }

    @Test
    void disableNextLineMissesOtherRulesAndOtherLines() {
        String source = """
                // nop-lint-disable-next-line demo/rule-one
                int one = violation();
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source,
                diagnostic(R2, rangeOf(tree, "violation()")));
        assertEquals(0, outcome.suppressed().size(), "another rule is not in scope");
        // the directive suppressed nothing for its own rule? no: rule-one has no candidate
        // on the next line, so the directive is unused.
        assertEquals(List.of(SuppressionMeta.UNUSED_DISABLE_DIRECTIVE), metaIds(outcome));
    }

    // ==================== disable-line ====================

    @Test
    void disableLineSuppressesItsOwnLineOnly() {
        String source = """
                int zero = violation(); // nop-lint-disable-line demo/rule-one
                int one = violation();
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source,
                diagnostic(R1, rangeOf(tree, "int zero = violation()")),
                diagnostic(R1, rangeOf(tree, "int one = violation()")));
        assertEquals(1, outcome.suppressed().size(), "line 1 is in scope, line 2 is not");
        assertEquals("int one = violation()", textOf(tree, outcome.diagnostics().get(0).range()));
    }

    @Test
    void multiLineDiagnosticOverlappingTheLineIsSuppressed() {
        String source = """
                int start = foo.invoke(
                    "x"); // nop-lint-disable-line
                """;
        LintTree tree = parse(source);
        io.nop.lint.core.node.LineIndex lines = new io.nop.lint.core.node.LineIndex(tree.source());
        // the candidate range spans lines 1-2, the span covers line 2
        Diagnostic multiLine = diagnostic(R1, rangeOf(tree, "foo.invoke(\n    \"x\")"));
        assertTrue(lines.startLine(multiLine.range()) != lines.endLine(multiLine.range()),
                "the candidate must be genuinely multi-line for this proof");
        SuppressionOutcome outcome = evaluate(source, multiLine);
        assertEquals(1, outcome.suppressed().size(), "overlap is enough (design 09 §2.2)");
    }

    // ==================== disable / enable pairing ====================

    @Test
    void disableWithRulesScopesUntilItsEnable() {
        String source = """
                void m() {
                    // nop-lint-disable demo/rule-one
                    int a = violation();
                    // nop-lint-enable demo/rule-one
                    int b = violation();
                }
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source,
                diagnostic(R1, rangeOf(tree, "int a = violation()")),
                diagnostic(R1, rangeOf(tree, "int b = violation()")));
        assertEquals(1, outcome.suppressed().size(), "enable restores the rule");
        assertEquals("int b = violation()", textOf(tree, outcome.diagnostics().get(0).range()));
        assertEquals(0, outcome.metaDiagnostics().size(), "paired disable/enable, both used");
    }

    @Test
    void disableOfAllRulesIsLiftedByEnableAll() {
        String source = """
                // nop-lint-disable
                int a = violation();
                // nop-lint-enable-all
                int b = violation();
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source,
                diagnostic(R1, rangeOf(tree, "int a = violation()")),
                diagnostic(R2, rangeOf(tree, "int b = violation()")));
        assertEquals(1, outcome.suppressed().size(), "only the all-rules region suppressed");
        assertEquals(R2, outcome.diagnostics().get(0).ruleId());
    }

    @Test
    void specificEnableDoesNotLiftABareDisable() {
        String source = """
                // nop-lint-disable
                int a = violation();
                // nop-lint-enable demo/rule-one
                int b = violation();
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source, diagnostic(R1, rangeOf(tree, "int b = violation()")));
        assertEquals(1, outcome.suppressed().size(),
                "a bare disable is lifted only by enable-all (design 09 §2 增注)");
        assertEquals(List.of(SuppressionMeta.UNPAIRED_DISABLE, SuppressionMeta.UNPAIRED_DISABLE),
                metaIds(outcome),
                "the unmatched enable and the still-open bare disable each raise their own entry");
    }

    @Test
    void disableCommaSeparatedRuleListScopesPerRule() {
        String source = """
                // nop-lint-disable demo/rule-one, demo/rule-two
                int a = violation();
                // nop-lint-enable demo/rule-one
                int b = violation();
                // nop-lint-enable-all
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source,
                diagnostic(R2, rangeOf(tree, "int b = violation()")));
        assertEquals(1, outcome.suppressed().size(), "rule-two stays disabled after rule-one's enable");
        assertTrue(metaIds(outcome).isEmpty(), "every directive closed and did suppress work");
    }

    // ==================== unpaired / EOF ====================

    @Test
    void disableOpenAtEofSuppressesToTheEndAndRaisesMeta() {
        String source = """
                // nop-lint-disable demo/rule-one
                int a = violation();
                """;
        LintTree tree = parse(source);
        SuppressionScan scan = scan(source);
        assertEquals(1, spanCount(scan), "the open disable still scopes to end of file");
        assertEquals(List.of(SuppressionMeta.UNPAIRED_DISABLE),
                scan.metaDiagnostics().stream().map(Diagnostic::ruleId).toList());
        assertEquals(SuppressionMeta.SEVERITY_UNPAIRED, scan.metaDiagnostics().get(0).severity());
        SuppressionOutcome outcome = evaluate(source, at(tree, "violation()"));
        assertEquals(1, outcome.suppressed().size(), "unpaired does not mean inactive");
    }

    @Test
    void enableWithoutOpenDisableRaisesUnpaired() {
        String source = "// nop-lint-enable demo/rule-one\n";
        SuppressionScan scan = scan(source);
        assertEquals(0, spanCount(scan));
        assertEquals(1, scan.metaDiagnostics().size());
        assertEquals(SuppressionMeta.UNPAIRED_DISABLE, scan.metaDiagnostics().get(0).ruleId());
    }

    @Test
    void enableAllWithoutOpenDisableRaisesUnpaired() {
        String source = "// nop-lint-enable-all\n";
        SuppressionScan scan = scan(source);
        assertEquals(0, spanCount(scan));
        assertEquals(1, scan.metaDiagnostics().size());
        assertEquals(SuppressionMeta.UNPAIRED_DISABLE, scan.metaDiagnostics().get(0).ruleId());
    }

    // ==================== unused directives ====================

    @Test
    void disableThatSuppressesNothingIsReportedUnused() {
        String source = "int clean = 1; // nop-lint-disable-line demo/rule-one\n";
        SuppressionOutcome outcome = evaluate(source);
        assertEquals(List.of(SuppressionMeta.UNUSED_DISABLE_DIRECTIVE), metaIds(outcome));
        assertEquals(SuppressionMeta.SEVERITY_UNUSED, outcome.metaDiagnostics().get(0).severity());
        assertTrue(outcome.metaDiagnostics().get(0).range().endByte()
                        > outcome.metaDiagnostics().get(0).range().startByte(),
                "the meta diagnostic points at the directive token");
    }

    @Test
    void directiveUsedByOneRuleIsNotUnused() {
        String source = """
                // nop-lint-disable demo/rule-one, demo/rule-two
                int a = violation();
                // nop-lint-enable-all
                int b = violation();
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source, at(tree, "int a = violation()"));
        assertEquals(1, outcome.suppressed().size());
        assertTrue(metaIds(outcome).isEmpty(),
                "the directive suppressed rule-one, so it is not unused despite rule-two's empty scope");
    }

    @Test
    void redundantDisableWithoutAnyHitIsUnused() {
        String source = """
                // nop-lint-disable demo/rule-one
                int a = violation();
                // nop-lint-disable demo/rule-one
                int b = violation();
                // nop-lint-enable-all
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source,
                at(tree, "int a = violation()"),
                at(tree, "int b = violation()"));
        assertEquals(2, outcome.suppressed().size(), "both regions suppress (first span wins attribution)");
        // the second directive opened no span of its own: its scope suppressed nothing new.
        // its region overlaps suppressed diagnostics, but attribution went to the first span.
        assertEquals(List.of(SuppressionMeta.UNUSED_DISABLE_DIRECTIVE), metaIds(outcome));
    }

    // ==================== carriers & positions ====================

    @Test
    void blockCommentCarriesDirectives() {
        String source = """
                /* nop-lint-disable-next-line demo/rule-one */
                int a = violation();
                int b = violation();
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source,
                at(tree, "int a = violation()"),
                at(tree, "int b = violation()"));
        assertEquals(1, outcome.suppressed().size());
    }

    @Test
    void blockCommentDecorationAroundDirectivesIsTolerated() {
        String source = """
                /*
                 * nop-lint-disable demo/rule-one --reason "legacy"
                 */
                int a = violation();
                /* nop-lint-enable demo/rule-one */
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source, at(tree, "violation()"));
        assertEquals(1, outcome.suppressed().size(),
                "star decoration and the dropped --reason suffix must not break parsing");
        assertTrue(metaIds(outcome).isEmpty());
    }

    @Test
    void directivesSurviveMultiByteContentBeforeThem() {
        String source = """
                // 中文注释 nop-lint-disable-next-line demo/rule-one
                int a = violation();
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source, at(tree, "violation()"));
        assertEquals(1, outcome.suppressed().size(),
                "byte offsets must account for multi-byte characters in the comment");
        assertTrue(metaIds(outcome).isEmpty());
    }

    @Test
    void directiveInStringLiteralIsNotASuppression() {
        String source = """
                String s = "nop-lint-disable demo/rule-one";
                int a = violation();
                """;
        LintTree tree = parse(source);
        SuppressionScan scan = scan(source);
        assertEquals(0, spanCount(scan), "string literals are not comments");
        assertTrue(scan.metaDiagnostics().isEmpty());
        SuppressionOutcome outcome = evaluate(source, at(tree, "violation()"));
        assertEquals(0, outcome.suppressed().size());
    }

    // ==================== fail-closed paths ====================

    @Test
    void enableWithoutRuleListFailsClosed() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> scan("// nop-lint-enable\n"));
        assertTrue(e.getMessage().contains("requires a rule list"));
    }

    @Test
    void enableAllWithArgumentsFailsClosed() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> scan("// nop-lint-enable-all demo/rule-one\n"));
        assertTrue(e.getMessage().contains("takes no arguments"));
    }

    @Test
    void trailingCommaFailsClosed() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> scan("// nop-lint-disable demo/rule-one,\n"));
        assertTrue(e.getMessage().contains("Empty rule name"));
    }

    @Test
    void invalidRuleTokenFailsClosed() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> scan("// nop-lint-disable demo/rule one!\n"));
        assertTrue(e.getMessage().contains("Invalid rule id"));
    }

    @Test
    void malformedKeywordContinuationFailsClosed() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> scan("// nop-lint-disable-next-lin demo/rule-one\nint a = 1;\n"));
        assertTrue(e.getMessage().contains("Invalid rule id"),
                "a near-miss keyword parses as 'disable' with '-next-lin' as argument, which "
                        + "is outside the rule-id alphabet: fail-closed, not silent");
    }

    @Test
    void twoDirectivesInOneCommentFailClosed() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> scan("// nop-lint-disable demo/rule-one nop-lint-enable demo/rule-two\n"));
        assertTrue(e.getMessage().contains("at most one directive per comment"));
    }

    @Test
    void proseWithSuffixedKeywordIsIgnored() {
        String source = """
                // nop-lint-disabled forever, someday nop-lint-enabled again
                int a = violation();
                """;
        SuppressionScan scan = scan(source);
        assertEquals(0, spanCount(scan), "'disabled'/'enabled' are not directive keywords");
    }

    @Test
    void hyphenatedMalformedKeywordFailsClosed() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> scan("// nop-lint-disable-everything today\nint a = 1;\n"));
        assertTrue(e.getMessage().contains("Invalid rule id"),
                "a hyphenated continuation parses as 'disable' with malformed arguments: "
                        + "fail-closed, not silent");
    }

    @Test
    void suffixedKeywordWithoutBoundaryIsProse() {
        String source = """
                // nop-lint-enabled forever
                int a = violation();
                """;
        SuppressionScan scan = scan(source);
        assertEquals(0, spanCount(scan), "'enabled' is not the 'enable' keyword");
    }

    // ==================== meta-diagnostics are not suppressible ====================

    @Test
    void metaDiagnosticsIgnoreSuppressionSpans() {
        String source = """
                // nop-lint-disable
                // nop-lint-enable demo/rule-one
                int a = violation();
                """;
        LintTree tree = parse(source);
        SuppressionOutcome outcome = evaluate(source, at(tree, "violation()"));
        // the candidate is suppressed by the still-open all-rules span...
        assertEquals(1, outcome.suppressed().size());
        // ...and the two pairing violations stand, unsuppressed by that same span.
        assertEquals(List.of(SuppressionMeta.UNPAIRED_DISABLE, SuppressionMeta.UNPAIRED_DISABLE),
                metaIds(outcome));
        assertEquals(2, outcome.diagnostics().size(),
                "the emitted list is the kept candidates plus the meta-diagnostics");
    }

    // ==================== provider composition ====================

    @Test
    void providerSpansParticipateInFilteringAndUnusedCheck() {
        String source = "int a = violation();\nint b = violation();\n";
        LintTree tree = parse(source);
        SuppressionProvider provider = t -> List.of(
                SuppressionSpan.allRules(rangeOf(tree, "int a = violation()"), rangeOf(tree, "int a")));
        SuppressionOutcome outcome = new SuppressionFilter(provider).evaluate(tree,
                List.of(at(tree, "int a = violation()"), at(tree, "int b = violation()")));
        assertEquals(1, outcome.suppressed().size(), "the annotation-equivalent span suppressed its scope");
        assertTrue(metaIds(outcome).isEmpty(), "the used provider span is not reported unused");
    }

    @Test
    void unusedProviderSpanIsReportedUnusedAtItsDirectiveRange() {
        String source = "int a = violation();\nint b = violation();\n";
        LintTree tree = parse(source);
        SuppressionProvider provider = t -> List.of(
                SuppressionSpan.allRules(rangeOf(tree, "int a = violation()"), rangeOf(tree, "int a")));
        SuppressionOutcome outcome = new SuppressionFilter(provider).evaluate(tree,
                List.of(at(tree, "int b = violation()")));
        assertEquals(0, outcome.suppressed().size(), "the span's scope holds no candidate");
        assertEquals(List.of(SuppressionMeta.UNUSED_DISABLE_DIRECTIVE), metaIds(outcome));
        assertEquals(rangeOf(tree, "int a"), outcome.metaDiagnostics().get(0).range(),
                "the unused entry points at the annotation-equivalent directive");
    }

    private static String textOf(LintTree tree, SourceRange range) {
        return new String(tree.source(), range.startByte(), range.length(), StandardCharsets.UTF_8);
    }
}
