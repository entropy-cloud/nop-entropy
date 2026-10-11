package io.nop.lint.core.pattern;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The meta-var consistency kernel (design 04 §2 {@code does_node_match_exactly}):
 * structural equality over independently parsed trees, plus the depth guard
 * that turns pathological deep nesting into a fail-closed module exception
 * instead of a {@link StackOverflowError} (plan 08, audit finding P3/M5).
 */
class TestNodeExactEquality {

    private static io.nop.lint.core.lang.LintLanguage java;

    @BeforeAll
    static void loadLanguage() {
        java = new TreeSitterLanguageAdapter("java",
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    @Test
    void identicalStructuresMatchAcrossIndependentParses() {
        LintTree a = java.parse("class A { void f(int x) { g(x, 1); } }");
        LintTree b = java.parse("class A { void f(int x) { g(x, 1); } }");

        assertTrue(NodeExactEquality.isExact(a.root(), b.root()),
                "identical sources are structurally exact");
    }

    @Test
    void textMismatchUnderSameKindFails() {
        LintTree a = java.parse("class A { void f() { g(1); } }");
        LintTree b = java.parse("class A { void f() { g(2); } }");

        assertTrue(!NodeExactEquality.isExact(a.root(), b.root()),
                "same kinds with different leaf text must not be exact");
    }

    @Test
    void pathologicalNestingFailsClosedAtTheDepthGuard() {
        // 80 nested parenthesized expressions: the pairwise descent would
        // recurse past the ceiling — the guard must raise the module
        // exception, never a StackOverflowError
        String deep = "class D { int x = " + "(".repeat(80) + "1" + ")".repeat(80) + "; }";
        LintTree a = java.parse(deep);
        LintTree b = java.parse(deep);

        NopLintException ex = assertThrows(NopLintException.class,
                () -> NodeExactEquality.isExact(a.root(), b.root()));
        assertTrue(ex.getMessage().contains("nesting levels"), ex.getMessage());
    }
}
