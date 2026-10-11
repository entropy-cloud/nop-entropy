package io.nop.lint.core.rule;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The relational horizon's local fail-closed invariant (plan 11 Phase 2,
 * audit finding C10a): the parser whitelist ({@code STOP_BY_VALUES}) guards
 * stopBy upstream, and the compiler's switch now carries its own explicit
 * {@code end} case (the parser normalizes a missing stopBy to "end") so the
 * default branch can reject without breaking every relational rule.
 * This test bypasses the parser (package-private constructors) to prove the
 * compiler-side guard stands alone.
 */
class TestStopByFailClosed {

    private static final io.nop.lint.core.lang.LintLanguage JAVA =
            new TreeSitterLanguageAdapter("java",
                    Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    @Test
    void explicitEndStillCompiles() {
        // built via the package-private constructors: inside with stopBy=end
        RuleDslModel.Relational relational =
                new RuleDslModel.Relational("foo()", "end", null, null, null, null);
        RuleDslModel.Matcher matcher = new RuleDslModel.Matcher(null, null, null, null,
                null, null, null, relational, null, null, null);
        // must NOT throw: the explicit end case keeps the common horizon alive
        io.nop.lint.core.engine.CompiledRule.compile(relationalMatcherModel(matcher), JAVA);
    }

    @Test
    void unknownStopByFailsClosedNamingTheValue() {
        RuleDslModel.Relational relational =
                new RuleDslModel.Relational("foo()", "sideways", null, null, null, null);
        RuleDslModel.Matcher matcher = new RuleDslModel.Matcher(null, null, null, null,
                null, null, null, relational, null, null, null);
        NopLintException ex = assertThrows(NopLintException.class,
                () -> io.nop.lint.core.engine.CompiledRule.compile(
                        relationalMatcherModel(matcher), JAVA));
        assertTrue(ex.getMessage().contains("sideways"), ex.getMessage());
        assertTrue(ex.getMessage().contains("neighbor|end|rule"), ex.getMessage());
    }

    private static RuleDslModel relationalMatcherModel(RuleDslModel.Matcher matcher) {
        // package-private model constructor: minimal single-rule model
        return new RuleDslModel("demo/stopby", "Java", "warning", "msg", matcher,
                java.util.List.of(), null, 500, java.util.Set.of(),
                java.util.Map.of(), java.util.Map.of(), null, null);
    }
}
