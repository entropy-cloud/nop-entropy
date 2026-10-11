package io.nop.lint.core.pattern;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintNode;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 focus tests: env capture/consistency/clone isolation, exact
 * equality paths, strictness decision matrix, named-leaf terminals.
 */
class MetaVarEnvTest {

    private static LintLanguage java;

    @BeforeAll
    static void loadLanguage() {
        java = new TreeSitterLanguageAdapter("java",
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    private static LintNode parseExpression(String source) {
        // program → expression_statement → <expr>
        return java.parse(source).root().children().get(0).children().get(0);
    }

    @Test
    void firstInsertCapturesSecondMustMatchExactly() {
        LintNode a = parseExpression("left");
        LintNode b = parseExpression("left");
        LintNode c = parseExpression("right");

        MetaVarEnv env = new MetaVarEnv();
        assertTrue(env.insert("X", a));
        assertSame(a, env.getCapture("X"));

        // Different tree positions with identical text: exact match.
        assertTrue(env.insert("X", b));
        assertSame(a, env.getCapture("X"), "first binding stays");

        // Different text: consistency violation, insert fails.
        assertFalse(env.insert("X", c));
        assertSame(a, env.getCapture("X"));
    }

    @Test
    void exactEqualityPaths() {
        LintNode left = parseExpression("a + b");
        LintNode right = parseExpression("a + b");

        // Different positions, structurally identical.
        assertTrue(NodeExactEquality.isExact(left, right));

        // Identical text under different kinds must not match (strictness
        // over upstream's text-only leaf comparison).
        LintNode ident = parseExpression("a");
        LintNode str = parseExpression("\"a\"");
        assertFalse(NodeExactEquality.isExact(ident, str));

        // Structural difference inside children.
        assertFalse(NodeExactEquality.isExact(left, parseExpression("a + c")));
    }

    @Test
    void multiCaptureAppendsInOrder() {
        LintNode x = parseExpression("1");
        LintNode y = parseExpression("2");

        MetaVarEnv env = new MetaVarEnv();
        env.insertMulti("A", List.of(x));
        env.insertMulti("A", List.of(y));
        assertEquals(List.of(x, y), env.getMultiCapture("A"));

        // Unbound multi name reads as null; zero-width capture reads as [].
        assertNull(env.getMultiCapture("Z"));
        env.insertMulti("EMPTY", List.of());
        assertNotNull(env.getMultiCapture("EMPTY"));
        assertTrue(env.getMultiCapture("EMPTY").isEmpty());
    }

    @Test
    void nonCapturingMultiNamesAreConsumedSilently() {
        MetaVarEnv env = new MetaVarEnv();
        env.insertMulti(null, List.of(parseExpression("1")));
        env.insertMulti("_HIDDEN", List.of(parseExpression("1")));
        assertNull(env.getMultiCapture(null));
        assertNull(env.getMultiCapture("_HIDDEN"));
    }

    @Test
    void cloneIsolatesProbeWrites() {
        LintNode node = parseExpression("x");
        MetaVarEnv env = new MetaVarEnv();
        assertTrue(env.insert("BASE", node));

        MetaVarEnv probe = env.clone();
        assertTrue(probe.insert("PROBE_ONLY", node));
        probe.insertMulti("M", List.of(node));

        assertNotNull(env.getCapture("BASE"));
        assertNull(env.getCapture("PROBE_ONLY"), "probe binding must not leak");
        assertNull(env.getMultiCapture("M"), "probe multi must not leak");

        // And the reverse direction: base writes after cloning stay in base.
        assertTrue(env.insert("LATE", node));
        assertNull(probe.getCapture("LATE"));
    }

    @Test
    void nullArgumentsAreRejectedOnInsert() {
        MetaVarEnv env = new MetaVarEnv();
        assertFalse(env.insert(null, parseExpression("x")));
        assertFalse(env.insert("X", null));
        assertNull(env.getCapture("X"));
    }

    @Test
    void strictnessDecisionMatrix() {
        LintNode comment = findFirstKind(java.parse("// c\nclass A {}").root(), "line_comment");
        assertNotNull(comment);
        LintNode token = parseExpression("a + b").children().get(1); // unnamed '+'
        assertFalse(token.isNamed());

        assertFalse(Strictness.SMART.skipGoalUnnamed());
        assertTrue(Strictness.AST.skipGoalUnnamed());

        assertTrue(Strictness.SMART.canSkipCandidate(token));
        assertTrue(Strictness.AST.canSkipCandidate(token));

        assertTrue(Strictness.SMART.canSkipCandidate(comment), "SMART skips comments");
        assertFalse(Strictness.AST.canSkipCandidate(comment), "AST does not skip comments (upstream)");
    }

    @Test
    void namedLeafCompilesToTextCarryingTerminal() {
        // B-1 model revision: named leaf keeps its text so `dao` cannot match `get`.
        SourcePattern pattern = SourcePatternCompiler.compile("$OBJ.dao()", java);
        InternalNode root = (InternalNode) pattern.root();
        TerminalNode callee = (TerminalNode) root.children().get(2);
        assertTrue(callee.named());
        assertEquals("dao", callee.text());
    }

    private static LintNode findFirstKind(LintNode root, String kind) {
        for (LintNode node : root) {
            if (node.kind().equals(kind)) {
                return node;
            }
        }
        return null;
    }
}
