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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Composite operator matrix (plan 2026-09-22-0544-2 Phase 2, design 04 §6):
 * {@code all} conjunction with scratchpad env and full-commit semantics,
 * {@code not} negation with probe env isolation, and their composition with
 * the relational operators.
 */
class TestCompositeMatcher {

    private static LintLanguage java;

    @BeforeAll
    static void loadLanguage() {
        java = new TreeSitterLanguageAdapter("java",
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    private static NodeMatcher pattern(String p) {
        return new PatternNodeMatcher(SourcePatternCompiler.compile(p, java));
    }

    private static LintNode nodeOf(String locator, String source) {
        List<Match> matches = SourcePatternCompiler.compile(locator, java)
                .matchIn(java.parse(source).root());
        assertEquals(1, matches.size(), "locator '" + locator + "' must be unambiguous in:\n" + source);
        return matches.get(0).node();
    }

    @Nested
    class All {

        @Test
        void everyChildMustMatchTheSameNode() {
            NodeMatcher all = new AllMatcher(List.of(
                    pattern("$OBJ.save($$$ARGS)"),
                    new KindNodeMatcher(java.kindId("method_invocation"))));
            LintNode call = nodeOf("$OBJ.save($$$ARGS)",
                    "class A { void f() { dao.save(1); } }");
            assertTrue(all.matches(call, new MetaVarEnv()));
            NodeMatcher mismatching = new AllMatcher(List.of(
                    pattern("$OBJ.save($$$ARGS)"),
                    new KindNodeMatcher(java.kindId("switch_expression"))));
            assertFalse(mismatching.matches(call, new MetaVarEnv()),
                    "a non-matching child rejects the whole conjunction");
        }

        @Test
        void bindingsOfAllChildrenCommitTogether() {
            NodeMatcher all = new AllMatcher(List.of(
                    pattern("$OBJ.save($$$ARGS)"),
                    pattern("$OBJ.$M($$$ARGS)")));
            LintNode call = nodeOf("$OBJ.save($$$ARGS)",
                    "class A { void f() { dao.save(1); } }");
            MetaVarEnv env = new MetaVarEnv();
            assertTrue(all.matches(call, env));
            assertEquals("dao", env.getCapture("OBJ").text());
            assertEquals("save", env.getCapture("M").text());
            assertNotNull(env.getMultiCapture("ARGS"));
        }

        @Test
        void partialConjunctionLeaksNothing() {
            NodeMatcher all = new AllMatcher(List.of(
                    pattern("$OBJ.save($$$ARGS)"),
                    pattern("$OBJ.wrong($$$ARGS)")));
            LintNode call = nodeOf("$OBJ.save($$$ARGS)",
                    "class A { void f() { dao.save(1); } }");
            MetaVarEnv env = new MetaVarEnv();
            assertFalse(all.matches(call, env));
            assertTrue(env.singleCaptures().isEmpty(),
                    "the first child's captures must not survive a failed conjunction");
        }

        @Test
        void emptyConjunctionIsRejectedAtConstruction() {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new AllMatcher(List.of()));
        }
    }

    @Nested
    class Not {

        @Test
        void negatesTheInnerMatch() {
            NodeMatcher notSave = new NotMatcher(pattern("$OBJ.save($$$ARGS)"));
            LintNode save = nodeOf("$OBJ.save($$$ARGS)",
                    "class A { void f() { dao.save(1); } }");
            LintNode load = nodeOf("$OBJ.load($$$ARGS)",
                    "class A { void f() { dao.load(1); } }");
            assertFalse(notSave.matches(save, new MetaVarEnv()), "inner matched -> not fails");
            assertTrue(notSave.matches(load, new MetaVarEnv()), "inner unmatched -> not holds");
        }

        @Test
        void innerBindingsNeverLeak() {
            NodeMatcher notSave = new NotMatcher(pattern("$OBJ.load($$$ARGS)"));
            LintNode load = nodeOf("$OBJ.load($$$ARGS)",
                    "class A { void f() { dao.load(1); } }");
            MetaVarEnv env = new MetaVarEnv();
            assertFalse(notSave.matches(load, env));
            assertTrue(env.singleCaptures().isEmpty(),
                    "the inner match's bindings are discarded with the probe");

            LintNode save = nodeOf("$OBJ.save($$$ARGS)",
                    "class A { void f() { dao.save(1); } }");
            assertTrue(notSave.matches(save, env));
            assertTrue(env.singleCaptures().isEmpty(),
                    "even a passing negation binds nothing");
        }

        @Test
        void composesWithRelationalMatchers() {
            // try { ... } catch that does NOT rethrow: has throw must fail
            String withThrow = "class A { void f() { try { a(); } catch (Exception e) { throw e; } } }";
            String withoutThrow = "class A { void f() { try { a(); } catch (Exception e) { log(); } } }";
            NodeMatcher notRethrow = new NotMatcher(new RelationalMatcher(
                    RelationalMatcher.Op.HAS, pattern("throw $$$"), StopBy.end(), null));

            LintNode catchWithThrow = firstOfKind("catch_clause", withThrow);
            LintNode catchWithoutThrow = firstOfKind("catch_clause", withoutThrow);
            assertFalse(notRethrow.matches(catchWithThrow, new MetaVarEnv()));
            assertTrue(notRethrow.matches(catchWithoutThrow, new MetaVarEnv()));
        }

        @Test
        void allWrappingNotAndHasEndToEnd() {
            // all: [kind catch_clause, not: has: throw $$$]
            NodeMatcher all = new AllMatcher(List.of(
                    new KindNodeMatcher(java.kindId("catch_clause")),
                    new NotMatcher(new RelationalMatcher(
                            RelationalMatcher.Op.HAS, pattern("throw $$$"), StopBy.end(), null))));
            String withThrow = "class A { void f() { try { a(); } catch (Exception e) { throw e; } } }";
            String withoutThrow = "class A { void f() { try { a(); } catch (Exception e) { log(); } } }";
            assertTrue(all.matches(firstOfKind("catch_clause", withoutThrow), new MetaVarEnv()));
            assertFalse(all.matches(firstOfKind("catch_clause", withThrow), new MetaVarEnv()));
        }

        private LintNode firstOfKind(String kind, String source) {
            for (LintNode node : java.parse(source).root()) {
                if (node.kind().equals(kind)) {
                    return node;
                }
            }
            throw new AssertionError("no node of kind '" + kind + "'");
        }
    }

    @Nested
    class DeepNesting {

        @Test
        void notOverAllOverPattern() {
            // not: all: [$OBJ.save($$$), kind: method_invocation]
            NodeMatcher notBoth = new NotMatcher(new AllMatcher(List.of(
                    pattern("$OBJ.save($$$ARGS)"),
                    new KindNodeMatcher(java.kindId("method_invocation")))));
            LintNode save = nodeOf("$OBJ.save($$$ARGS)",
                    "class A { void f() { dao.save(1); } }");
            assertFalse(notBoth.matches(save, new MetaVarEnv()));
            assertTrue(notBoth.matches(nodeOf("$OBJ.load($$$ARGS)",
                    "class A { void f() { dao.load(1); } }"), new MetaVarEnv()));
        }
    }
}
