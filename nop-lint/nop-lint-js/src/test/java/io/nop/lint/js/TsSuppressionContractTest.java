package io.nop.lint.js;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.suppress.CommentSuppressionScanner;
import io.nop.lint.core.suppress.SuppressionScan;
import io.nop.lint.core.suppress.SuppressionSpan;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suppression adaptation proof (plan item 19 Phase 1): both bindings keep
 * the null {@code suppressionProvider()} contract (no annotation-carried
 * extractor in the v1 scope), and the language-agnostic
 * {@link CommentSuppressionScanner} recognizes the TS comment shapes —
 * {@code //} line and {@code /* block *}{@code /} — so the inline-comment
 * suppression path is the documented, working default for TS/TSX, not a
 * silent fallback.
 */
class TsSuppressionContractTest {

    private static String slice(byte[] source, io.nop.lint.core.node.SourceRange range) {
        return new String(source, range.startByte(), range.length(), StandardCharsets.UTF_8);
    }

    @Test
    void bothBindingsDeclareNoAnnotationSuppressionProvider() {
        assertNull(TypeScriptLanguage.get().suppressionProvider());
        assertNull(TsxLanguage.get().suppressionProvider());
    }

    @Test
    void lineCommentDirectiveProducesNextLineSpan() {
        String source = """
                // nop-lint-disable-next-line demo/rule
                const a = 1;
                const b = 2;
                """;
        LintTree tree = TypeScriptLanguage.get().parse(source);
        SuppressionScan scan = new CommentSuppressionScanner().scan(tree);

        assertEquals(1, scan.spans().size(), "one disable-next-line span expected");
        SuppressionSpan span = scan.spans().get(0);
        assertTrue(span.covers("demo/rule"), "the span must cover the named rule");
        assertTrue(slice(tree.source(), span.range()).contains("const a = 1;"),
                "the span must scope to the next line, got: "
                        + slice(tree.source(), span.range()));
    }

    @Test
    void blockCommentDirectiveProducesOpenEndedSpanWithMetaDiagnostic() {
        String source = """
                const keep = 1;
                /* nop-lint-disable demo/other */
                const suppressed = 2;
                """;
        LintTree tree = TypeScriptLanguage.get().parse(source);
        SuppressionScan scan = new CommentSuppressionScanner().scan(tree);

        assertEquals(1, scan.spans().size(), "one open-ended disable span expected");
        SuppressionSpan span = scan.spans().get(0);
        assertTrue(span.covers("demo/other"), "the span must cover the named rule");
        assertTrue(slice(tree.source(), span.range()).contains("const suppressed = 2;"),
                "the open disable must suppress to end of file");

        assertEquals(1, scan.metaDiagnostics().size(),
                "a disable never closed by an enable raises the explicit unpaired meta");
    }

    @Test
    void blockCommentWithoutDirectivesYieldsNoSpans() {
        String source = """
                /* plain prose comment */
                const a = 1; // trailing prose
                """;
        LintTree tree = TypeScriptLanguage.get().parse(source);
        SuppressionScan scan = new CommentSuppressionScanner().scan(tree);

        assertEquals(0, scan.spans().size(), "plain comments must not suppress");
        assertEquals(0, scan.metaDiagnostics().size());
    }

    @Test
    void commentShapesAreGrammarExtrasOnBothBindings() {
        for (LintLanguage language : List.of(TypeScriptLanguage.get(), TsxLanguage.get())) {
            LintNode root = language.parse("// line\n/* block */\nconst a = 1;").root();
            long comments = 0;
            for (LintNode node : root) {
                if (node.isExtra() || node.kind().contains("comment")) {
                    comments++;
                }
            }
            assertEquals(2, comments,
                    "the scanner's comment vehicle must see both shapes on " + language.id());
        }
    }
}
