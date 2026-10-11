package io.nop.lint.core.fix;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.lint.core.pattern.SourcePattern;
import io.nop.lint.core.pattern.SourcePatternCompiler;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 1 matrix of the autofix surface (roadmap item 25, plan
 * 2026-09-22-2225-1): template round-trips (single capture, sequence slice
 * with separators preserved, empty sequence, literals), the fail-closed
 * rejections (undeclared captures, undeclared {@code $TOKEN}, fix+xscript,
 * fix on the XML path), and the Fixer merge (disjoint keeps all, overlap
 * keeps the priority winner).
 */
public class TestTemplateFixAndMerge {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    // ==================== TemplateFix ====================

    @Test
    public void singleCaptureRendersNodeText() {
        SourcePattern pattern = SourcePatternCompiler.compile("throw $ERR", JAVA);
        LintTree tree = JAVA.parse("class T { void m() { throw new Error(\"x\"); } }");
        var match = pattern.matchIn(tree.root()).get(0);

        TemplateFix fix = TemplateFix.compile("demo/t", "log($ERR);",
                pattern.captureNames(), pattern.multiCaptureNames());
        String rendered = fix.apply(match.env(), tree.source());
        assertEquals("log(new Error(\"x\"));", rendered);
    }

    @Test
    public void sequenceCaptureSlicesRawSourceBetweenFirstAndLastNodes() {
        SourcePattern pattern = SourcePatternCompiler.compile("m($$$ARGS)", JAVA);
        LintTree tree = JAVA.parse("class T { void m() { m(a, b, c); } }");
        var match = pattern.matchIn(tree.root()).get(0);

        TemplateFix fix = TemplateFix.compile("demo/s", "m2($$$ARGS);",
                pattern.captureNames(), pattern.multiCaptureNames());
        assertEquals("m2(a, b, c);", fix.apply(match.env(), tree.source()),
                "the raw slice keeps the separators verbatim");
    }

    @Test
    public void emptySequenceRendersAsEmptyString() {
        SourcePattern pattern = SourcePatternCompiler.compile("m($$$ARGS)", JAVA);
        LintTree tree = JAVA.parse("class T { void m() { m(); } }");
        var match = pattern.matchIn(tree.root()).get(0);

        TemplateFix fix = TemplateFix.compile("demo/e", "m2($$$ARGS);",
                pattern.captureNames(), pattern.multiCaptureNames());
        assertEquals("m2();", fix.apply(match.env(), tree.source()));
    }

    @Test
    public void literalsSurviveVerbatim() {
        TemplateFix fix = TemplateFix.compile("demo/l", "logger.info(\"done\");", Set.of(), Set.of());
        assertEquals("logger.info(\"done\");", fix.apply(new MetaVarEnv(), new byte[0]));
    }

    @Test
    public void undeclaredCaptureReferenceRejected() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> TemplateFix.compile("demo/bad", "$NOPE", Set.of("OTHER"), Set.of()));
        assertTrue(ex.getMessage().contains("demo/bad"));
        assertTrue(ex.getMessage().contains("$NOPE"), ex.getMessage());
    }

    @Test
    public void undeclaredDollarTokenRejectedAsNoEscapeSyntax() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> TemplateFix.compile("demo/bad", "replace(\"$1\", x)", Set.of(), Set.of()));
        assertTrue(ex.getMessage().contains("no escape syntax"), ex.getMessage());
    }

    // ==================== Fixer merge ====================

    private Fix fix(int start, int end, int order) {
        return new Fix(new io.nop.lint.core.node.SourceRange(start, end), "r" + order,
                "rule/" + order, "d", order);
    }

    @Test
    public void disjointFixesAllKeptAndSortedByRangeStart() {
        Fixer.MergeResult result = Fixer.merge(List.of(fix(10, 20, 0), fix(0, 5, 1), fix(30, 35, 2)));
        assertEquals(0, result.skippedConflicts());
        assertEquals(3, result.applied().size());
        assertEquals(0, result.applied().get(0).range().startByte());
        assertEquals(10, result.applied().get(1).range().startByte());
        assertEquals(30, result.applied().get(2).range().startByte());
    }

    @Test
    public void overlappingFixesKeepThePriorityWinner() {
        Fixer.MergeResult result = Fixer.merge(List.of(fix(10, 20, 0), fix(15, 25, 1), fix(0, 30, 2)));
        assertEquals(2, result.skippedConflicts(), "both later candidates overlap the winner");
        assertEquals(1, result.applied().size());
        assertEquals(10, result.applied().get(0).range().startByte(),
                "the first-priority candidate wins regardless of range start");
    }

    @Test
    public void identicalRangesConflictLikewise() {
        Fixer.MergeResult result = Fixer.merge(List.of(fix(5, 10, 0), fix(5, 10, 1)));
        assertEquals(1, result.skippedConflicts());
        assertEquals(1, result.applied().size());
    }

}
