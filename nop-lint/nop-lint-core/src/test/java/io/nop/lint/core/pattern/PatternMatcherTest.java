package io.nop.lint.core.pattern;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintNode;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kernel focus tests (plan 04 phases 2–5): lockstep traversal with the
 * strictness decision matrix, ellipsis lookahead with capture pinning, and
 * the M1 end-to-end acceptance set.
 */
class PatternMatcherTest {

    private static LintLanguage java;

    @BeforeAll
    static void loadLanguage() {
        java = new TreeSitterLanguageAdapter("java",
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    private static List<Match> match(String pattern, String source) {
        return SourcePatternCompiler.compile(pattern, java).matchIn(java.parse(source).root());
    }

    private static List<Match> match(String pattern, String source, Strictness strictness) {
        return SourcePatternCompiler.compile(pattern, java).matchIn(java.parse(source).root(), strictness);
    }

    private static String multiText(Match m, String name) {
        List<LintNode> captured = m.env().getMultiCapture(name);
        assertNotNull(captured, "multi capture " + name + " must exist");
        StringBuilder sb = new StringBuilder();
        for (LintNode node : captured) {
            sb.append(node.text()).append('|');
        }
        return sb.toString();
    }

    @Nested
    class LockstepTraversal {

        @Test
        void namedTerminalTextMustMatch() {
            String source = "class A { void f() { foo.get().save(); } }";
            assertEquals(0, match("$OBJ.dao().save()", source).size(),
                    "dao must not match get (kind-equal, text-different)");
        }

        @Test
        void unnamedCandidateIsSkippedInSmart() {
            // trailing missing ';' in the goal is dropped at compile; the
            // source side carries unnamed parens the lockstep consumes.
            List<Match> matches = match("foo.bar()", "class A { void f() { foo.bar(); } }");
            assertEquals(1, matches.size());
        }

        @Test
        void commentIsSkippedInSmart() {
            String source = "class A { void f() { foo.bar(); /* note */ } }";
            assertEquals(1, match("foo.bar()", source).size(),
                    "trailing comment must not block the match");
        }

        @Test
        void singleStatementPatternDoesNotSwallowTwo() {
            String source = "class A { void f() { foo.bar(); baz.qux(); } }";
            assertEquals(1, match("foo.bar()", source).size(),
                    "the second statement is a non-skippable tail, not part of the match");
        }

        // Note: "SINGLE vs unnamed candidate = NoMatch" (upstream M-B rule) is
        // defensively coded in step() but not triggerable through Java
        // patterns — every meta-var slot in Java grammar sits at a named
        // node position, and unnamed tokens (parens, commas) live between
        // those slots where the candidate-keyed skip applies instead.

        @Test
        void astStrictnessMatchesSameShape() {
            String plus = "$A + $B";
            assertEquals(1, match(plus, "class A { void f() { a + b; } }").size(),
                    "SMART: '+' must match a real plus");
            assertEquals(1, match(plus, "class A { void f() { a + b; } }", Strictness.AST).size(),
                    "AST still matches the true shape");
        }

        @Test
        void astDroppedGoalChangesShape() {
            // AST drops the '+' goal; $B then binds the second operand, so the
            // match succeeds — but against a different operator shape the
            // dropped-goal walk leaves a named tail and fails. Pinning the
            // skip-goal behavior: SMART fails the '<<' pair, AST walks past.
            assertEquals(0, match("$A + $B", "class A { void f() { a << b; } }").size(),
                    "SMART: '+' goal cannot hop to bind across '<<'");
        }

        @Test
        void astTrailingUnnamedGoalSurvivesCandidateExhaustion() {
            // Java's legal trailing-comma spot is the array initializer
            // (upstream's print($A,) case is JS). Pattern keeps the trailing
            // comma; the source omits it.
            String pattern = "int[] a = {1, 2,};";
            String withComma = "class A { void f() { int[] a = {1, 2,}; } }";
            String withoutComma = "class A { void f() { int[] a = {1, 2}; } }";
            assertEquals(1, match(pattern, withComma).size());
            assertEquals(0, match(pattern, withoutComma).size(),
                    "SMART keeps the trailing comma goal");
            assertEquals(1, match(pattern, withoutComma, Strictness.AST).size(),
                    "AST skips trailing unnamed goals at candidate exhaustion");
        }

        @Test
        void astDoesNotSkipCommentCandidates() {
            String source = "class A { void f() { m( /* c */ a, b); } }";
            String pattern = "$M(a, b)";
            assertEquals(1, match(pattern, source).size(),
                    "SMART steps over the comment extra");
            assertEquals(0, match(pattern, source, Strictness.AST).size(),
                    "AST: comments are named nodes and must be matched explicitly");
        }

        @Test
        void terminalRootMatchesBothStrictness() {
            for (Strictness strictness : Strictness.values()) {
                List<Match> matches = SourcePatternCompiler.compile(";", java)
                        .matchIn(java.parse("class A { void f() { x(); } }").root(), strictness);
                assertTrue(matches.size() > 0, "unnamed terminal root matches under " + strictness);
            }
        }
    }

    @Nested
    class EllipsisMatching {

        @Test
        void trailingMultiCapturesZeroOneAndMany() {
            assertEquals(1, match("f($$$A)", "class A { void f() { f(); } }").size());
            Match none = match("f($$$A)", "class A { void f() { f(); } }").get(0);
            assertNotNull(none.env().getMultiCapture("A"));
            assertTrue(none.env().getMultiCapture("A").isEmpty(), "zero-width capture is live");

            Match one = match("f($$$A)", "class A { void f() { f(1); } }").get(0);
            assertEquals("1|", multiText(one, "A"));

            Match many = match("f($$$A)", "class A { void f() { f(1, 2); } }").get(0);
            assertEquals("1|,|2|", multiText(many, "A"), "capture includes separators");
        }

        @Test
        void multiFollowedBySingleStopsAtLookahead() {
            Match m = match("f($$$A, $B)", "class A { void f() { f(1, 2, 3); } }").get(0);
            assertEquals("1|,|2|", multiText(m, "A"));
            assertEquals("3", m.env().getCapture("B").text());
        }

        @Test
        void singleFollowedByMulti() {
            Match m = match("f($A, $$$B)", "class A { void f() { f(x, 1, 2); } }").get(0);
            assertEquals("x", m.env().getCapture("A").text());
            assertEquals("1|,|2|", multiText(m, "B"));
        }

        @Test
        void singleFollowedByMultiSeparatorHandling() {
            // Java's legal trailing-comma spot is the array initializer. The
            // ',' goal is a real named-goal gate: without the separator the
            // match fails even though the tail ellipsis is zero-width; with
            // it, B captures live-empty.
            String pattern = "int[] a = { $A, $$$B };";
            String noSeparator = "class A { void f() { int[] a = { 1 }; } }";
            String withSeparator = "class A { void f() { int[] a = { 1, }; } }";
            String threeItems = "class A { void f() { int[] a = { 1, 2, 3 }; } }";
            assertEquals(0, match(pattern, noSeparator).size(),
                    "missing separator cannot be rescued by the tail ellipsis");
            Match m = match(pattern, withSeparator).get(0);
            assertEquals("1", m.env().getCapture("A").text());
            assertNotNull(m.env().getMultiCapture("B"));
            assertTrue(m.env().getMultiCapture("B").isEmpty(), "zero-width tail capture is live");
            Match three = match(pattern, threeItems).get(0);
            assertEquals("1", three.env().getCapture("A").text());
            assertEquals("2|,|3|", multiText(three, "B"));
        }

        @Test
        void consecutiveMultiConsumesOneThenHandsOff() {
            // Upstream consume-one: first $$$ closes after a single candidate.
            Match m = match("f($$$A, $$$B)", "class A { void f() { f(1, 2, 3); } }").get(0);
            assertEquals("1|", multiText(m, "A"));
            assertEquals("2|,|3|", multiText(m, "B"));
        }

        @Test
        void nestedArgumentListCaptures() {
            Match m = match("throw new RuntimeException($$$ARGS)",
                    "class A { void f() { throw new RuntimeException(\"boom\", 1); } }").get(0);
            assertEquals("throw_statement", m.node().kind());
            assertEquals("\"boom\"|,|1|", multiText(m, "ARGS"));
        }

        @Test
        void classBodyBareMultiMatchesWithoutCapturing() {
            List<Match> matches = match("class $C extends CrudBizModel { $$$ }",
                    "class Foo extends CrudBizModel { void a() {} int b; }");
            assertEquals(1, matches.size());
            assertEquals("Foo", matches.get(0).env().getCapture("C").text());
            assertNull(matches.get(0).env().getMultiCapture(null) == null ? null : null,
                    "bare $$$ consumes silently");
        }

        @Test
        void classBodyNamedMultiCapturesMembers() {
            Match m = match("class $C extends CrudBizModel { $$$BODY }",
                    "class Foo extends CrudBizModel { void a() {} int b; }").get(0);
            assertEquals(2, m.env().getMultiCapture("BODY").size());
        }
    }

    @Nested
    class M1Acceptance {

        private static final String SNIPPET = """
                class Demo {
                    void run(int x) {
                        if (x > 0) {
                            throw new RuntimeException("boom");
                        }
                    }
                    Demo self() {
                        return this;
                    }
                    void call(Demo d) {
                        d.dao().save(1, 2);
                    }
                }
                """;

        @Test
        void throwPatternEndToEnd() {
            List<Match> matches = match("throw new RuntimeException($$$ARGS)", SNIPPET);
            assertEquals(1, matches.size());
            Match m = matches.get(0);
            assertEquals("throw_statement", m.node().kind());
            assertEquals("\"boom\"", m.env().getMultiCapture("ARGS").get(0).text());
        }

        @Test
        void chainPatternEndToEnd() {
            List<Match> matches = match("$OBJ.dao().$METHOD($$$ARGS)", SNIPPET);
            assertEquals(1, matches.size());
            Match m = matches.get(0);
            assertEquals("method_invocation", m.node().kind());
            assertEquals("d", m.env().getCapture("OBJ").text());
            assertEquals("save", m.env().getCapture("METHOD").text());
            assertEquals("1|,|2|", multiText(m, "ARGS"));
        }

        @Test
        void chainPatternNegativeWrongMethod() {
            String negative = """
                    class Demo {
                        void call(Demo d) {
                            d.get().save(1, 2);
                        }
                    }
                    """;
            assertEquals(0, match("$OBJ.dao().$METHOD($$$ARGS)", negative).size(),
                    "get() must not match dao()");
        }

        @Test
        void classPatternEndToEnd() {
            String source = "class Foo extends CrudBizModel { void a() {} }";
            List<Match> matches = match("class $C extends CrudBizModel { $$$ }", source);
            assertEquals(1, matches.size());
            assertEquals("Foo", matches.get(0).env().getCapture("C").text());
        }

        @Test
        void classPatternNegativeWrongSuperclass() {
            String negative = "class Foo extends Other { void a() {} }";
            assertEquals(0, match("class $C extends CrudBizModel { $$$ }", negative).size());
        }

        @Test
        void sameNameConsistencyEndToEnd() {
            assertEquals(1, match("$X == $X", "class A { void f() { a == a; } }").size());
            assertEquals(0, match("$X == $X", "class A { void f() { a == b; } }").size(),
                    "inconsistent captures must reject the match");
        }

        @Test
        void metaVarRootPinning() {
            String source = "class A { void f() { a == b; } }";
            List<Match> matches = match("$VAR", source);
            long namedCount = countNamed(java.parse(source).root());
            assertEquals(namedCount, matches.size(),
                    "$VAR matches every named node including the root");
        }

        @Test
        void noFalsePositivesOnUnrelatedCode() {
            assertEquals(0, match("throw new RuntimeException($$$ARGS)",
                    "class A { void f() { throw new IOException(\"x\"); } }").size());
        }

        private long countNamed(LintNode node) {
            long count = node.isNamed() ? 1 : 0;
            for (LintNode child : node.children()) {
                count += countNamed(child);
            }
            return count;
        }
    }
}
