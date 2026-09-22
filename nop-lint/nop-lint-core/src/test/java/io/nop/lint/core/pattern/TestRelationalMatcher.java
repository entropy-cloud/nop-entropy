package io.nop.lint.core.pattern;

import io.nop.lint.core.NopLintException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relational operator matrix (plan 2026-09-22-0544-2 Phase 1, design 04 §5):
 * every operator × every stopBy horizon × the boundary cells (hit / miss /
 * stop short-circuit including the stop node itself / field filtering), plus
 * the interaction with the existing strictness and trivial-node skipping and
 * the probe-environment isolation contract.
 */
class TestRelationalMatcher {

    private static LintLanguage java;

    @BeforeAll
    static void loadLanguage() {
        java = new TreeSitterLanguageAdapter("java",
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    private static NodeMatcher pattern(String p) {
        return new PatternNodeMatcher(SourcePatternCompiler.compile(p, java));
    }

    private static RelationalMatcher relational(RelationalMatcher.Op op, NodeMatcher inner, StopBy stopBy,
                                                String field) {
        return new RelationalMatcher(op, inner, stopBy, field);
    }

    private static LintNode root(String source) {
        return java.parse(source).root();
    }

    /**
     * The candidate node the relational matcher is applied to: the first
     * pre-order match of an unambiguous locator pattern.
     */
    private static LintNode nodeOf(String locator, String source) {
        List<Match> matches = SourcePatternCompiler.compile(locator, java).matchIn(root(source));
        assertEquals(1, matches.size(), "locator '" + locator + "' must be unambiguous in:\n" + source);
        return matches.get(0).node();
    }

    /**
     * The first pre-order node of the given kind — for constructs (like
     * {@code catch} clauses) whose source shape is not parseable as a
     * standalone pattern snippet.
     */
    private static LintNode firstOfKind(String kind, String source) {
        for (LintNode node : root(source)) {
            if (node.kind().equals(kind)) {
                return node;
            }
        }
        throw new AssertionError("no node of kind '" + kind + "' in:\n" + source);
    }

    private static boolean match(NodeMatcher matcher, LintNode node) {
        return matcher.matches(node, new MetaVarEnv());
    }

    // ==================== inside ====================

    @Nested
    class Inside {

        private static final String TRY_SOURCE =
                "class A { void f() { try { bar(); } catch (Exception e) { baz(); } } }";

        @Test
        void neighborChecksOnlyTheDirectParent() {
            // bar()'s parent is its expression_statement; the statement's
            // parent is the try-body block.
            LintNode statement = nodeOf("bar();", TRY_SOURCE);
            assertTrue(match(relational(RelationalMatcher.Op.INSIDE, pattern("{ bar(); }"),
                            StopBy.neighbor(), null), statement),
                    "the block is the statement's direct parent and matches");
            assertFalse(match(relational(RelationalMatcher.Op.INSIDE,
                            pattern("try { bar(); } catch (Exception $E) { $$$ }"),
                            StopBy.neighbor(), null), statement),
                    "the try_statement is a grandparent: beyond the neighbor horizon");
        }

        @Test
        void endWalksAllAncestors() {
            LintNode call = nodeOf("bar()", TRY_SOURCE);
            assertTrue(match(relational(RelationalMatcher.Op.INSIDE,
                            pattern("try { bar(); } catch (Exception $E) { $$$ }"),
                            StopBy.end(), null), call),
                    "end must reach the enclosing try_statement");
            assertTrue(match(relational(RelationalMatcher.Op.INSIDE,
                            new KindNodeMatcher(java.kindId("method_declaration")),
                            StopBy.end(), null), call),
                    "end must reach the enclosing method");
        }

        @Test
        void ruleModeHaltsInclusivelyAtTheStopNode() {
            LintNode call = nodeOf("bar()", TRY_SOURCE);
            // the stop node (try_statement) itself is offered to the finder
            // before the traversal halts — inclusive_until semantics.
            assertTrue(match(relational(RelationalMatcher.Op.INSIDE,
                            pattern("try { bar(); } catch (Exception $E) { $$$ }"),
                            StopBy.rule(new KindNodeMatcher(java.kindId("try_statement"))), null), call),
                    "the stop node itself participates in the inner match");
            // a finder beyond the stop node never sees a match
            assertFalse(match(relational(RelationalMatcher.Op.INSIDE,
                            new KindNodeMatcher(java.kindId("method_declaration")),
                            StopBy.rule(new KindNodeMatcher(java.kindId("try_statement"))), null), call),
                    "the method_declaration lies beyond the try_statement stop");
            // end on the same finder hits, isolating the halt to the stop rule
            assertTrue(match(relational(RelationalMatcher.Op.INSIDE,
                    new KindNodeMatcher(java.kindId("method_declaration")), StopBy.end(), null), call));
        }

        @Test
        void fieldConstrainsTheCandidateSlot() {
            String source = "class A { void f() { if (cond) { bar(); } } }";
            LintNode block = nodeOf("{ bar(); }", source);
            assertTrue(match(relational(RelationalMatcher.Op.INSIDE, pattern("if ($C) { $$$ }"),
                            StopBy.neighbor(), "consequence"), block),
                    "the block occupies the if_statement's consequence slot");
            assertFalse(match(relational(RelationalMatcher.Op.INSIDE, pattern("if ($C) { $$$ }"),
                            StopBy.neighbor(), "condition"), block),
                    "the block does not occupy the condition slot");
            assertFalse(match(relational(RelationalMatcher.Op.INSIDE, pattern("if ($C) { $$$ }"),
                            StopBy.neighbor(), "nonexistent"), block),
                    "an unknown field slot never qualifies");
        }

        @Test
        void rootCandidateHasNoAncestors() {
            LintNode treeRoot = root("bar();");
            assertFalse(match(relational(RelationalMatcher.Op.INSIDE, pattern("bar();"),
                    StopBy.end(), null), treeRoot));
        }

        @Test
        void commentInsideTheAncestorDoesNotBlockTheMatch() {
            String source = "class A { void f() { try { /* note */ bar(); } catch (Exception e) { baz(); } } }";
            LintNode call = nodeOf("bar()", source);
            assertTrue(match(relational(RelationalMatcher.Op.INSIDE,
                            pattern("try { bar(); } catch (Exception $E) { $$$ }"),
                            StopBy.end(), null), call),
                    "SMART trivial skipping keeps the comment out of the ancestor lockstep");
        }
    }

    // ==================== has ====================

    @Nested
    class Has {

        private static final String IF_SOURCE = "class A { void f() { if (c) { bar(); } } }";

        @Test
        void neighborChecksOnlyDirectChildren() {
            LintNode ifStatement = nodeOf("if (c) { $$$ }", IF_SOURCE);
            assertTrue(match(relational(RelationalMatcher.Op.HAS, pattern("{ bar(); }"),
                            StopBy.neighbor(), null), ifStatement),
                    "the block is a direct child");
            assertFalse(match(relational(RelationalMatcher.Op.HAS, pattern("bar();"),
                            StopBy.neighbor(), null), ifStatement),
                    "bar() sits two levels deep: beyond the neighbor horizon");
            assertTrue(match(relational(RelationalMatcher.Op.HAS, pattern("bar();"),
                            StopBy.end(), null), ifStatement),
                    "end reaches the grandchild");
        }

        @Test
        void ruleModeHaltsInclusivelyAtTheStopNode() {
            String source = "class A { void f() { { foo(); bar(); } } }";
            LintNode block = nodeOf("{ foo(); bar(); }", source);
            // descendants in pre-order: foo-stmt, bar-stmt. A stop rule that
            // matches the FIRST descendant halts before bar() is ever tried.
            assertFalse(match(relational(RelationalMatcher.Op.HAS, pattern("bar();"),
                            StopBy.rule(pattern("foo();")), null), block),
                    "the stop node foo-stmt precedes bar() and halts the take-while");
            // the stop node itself still participates in the inner match
            assertTrue(match(relational(RelationalMatcher.Op.HAS, pattern("foo();"),
                            StopBy.rule(pattern("foo();")), null), block),
                    "inclusive_until: the stop node is offered to the finder first");
        }

        @Test
        void fieldRootsTheSearchAtTheFieldChild() {
            String source = "class A { void f() { try { } catch (Exception e) { bar(); } } }";
            LintNode catchClause = firstOfKind("catch_clause", source);
            assertTrue(match(relational(RelationalMatcher.Op.HAS, pattern("bar();"),
                            StopBy.neighbor(), "body"), catchClause),
                    "the body field child contains bar() one level down");
            assertFalse(match(relational(RelationalMatcher.Op.HAS, pattern("bar();"),
                            StopBy.end(), "parameter"), catchClause),
                    "the parameter field subtree cannot contain the statement");
            String noBody = "class A { void f() { try { } catch (Exception e) { } } }";
            LintNode emptyCatch = firstOfKind("catch_clause", noBody);
            assertFalse(match(relational(RelationalMatcher.Op.HAS, pattern("bar();"),
                    StopBy.end(), "body"), emptyCatch), "an empty body subtree has no hit");
        }

        @Test
        void missingFieldChildFailsWithoutTraversal() {
            LintNode block = nodeOf("{ bar(); }", "class A { void f() { { bar(); } } }");
            assertFalse(match(relational(RelationalMatcher.Op.HAS, pattern("bar();"),
                    StopBy.end(), "body"), block), "blocks have no body field");
        }

        @Test
        void leafCandidateHasNoDescendants() {
            LintNode leaf = nodeOf("c", "class A { void f() { if (c) { bar(); } } }");
            assertFalse(match(relational(RelationalMatcher.Op.HAS, pattern("c"),
                    StopBy.end(), null), leaf), "identifier has no children to search");
        }
    }

    // ==================== follows / precedes ====================

    @Nested
    class FollowsPrecedes {

        private static final String SEQ = "class A { void f() { foo(); mid(); bar(); } }";

        @Test
        void followsNeighborIsTheDirectlyPrecedingSibling() {
            LintNode bar = nodeOf("bar();", SEQ);
            assertTrue(match(relational(RelationalMatcher.Op.FOLLOWS, pattern("mid();"),
                            StopBy.neighbor(), null), bar));
            assertFalse(match(relational(RelationalMatcher.Op.FOLLOWS, pattern("foo();"),
                            StopBy.neighbor(), null), bar),
                    "foo() is two siblings back: beyond the neighbor horizon");
            assertTrue(match(relational(RelationalMatcher.Op.FOLLOWS, pattern("foo();"),
                            StopBy.end(), null), bar),
                    "end walks all previous siblings");
        }

        @Test
        void precedesNeighborIsTheDirectlyFollowingSibling() {
            LintNode foo = nodeOf("foo();", SEQ);
            assertTrue(match(relational(RelationalMatcher.Op.PRECEDES, pattern("mid();"),
                            StopBy.neighbor(), null), foo));
            assertFalse(match(relational(RelationalMatcher.Op.PRECEDES, pattern("bar();"),
                            StopBy.neighbor(), null), foo));
            assertTrue(match(relational(RelationalMatcher.Op.PRECEDES, pattern("bar();"),
                            StopBy.end(), null), foo));
        }

        @Test
        void followsRuleModeHaltsInclusively() {
            LintNode bar = nodeOf("bar();", SEQ);
            // previous siblings nearest-first: mid, foo. The stop at mid
            // halts before foo is tried.
            assertFalse(match(relational(RelationalMatcher.Op.FOLLOWS, pattern("foo();"),
                            StopBy.rule(pattern("mid();")), null), bar),
                    "the stop at mid() halts the take-while before foo()");
            assertTrue(match(relational(RelationalMatcher.Op.FOLLOWS, pattern("mid();"),
                            StopBy.rule(pattern("mid();")), null), bar),
                    "the stop node itself participates in the inner match");
        }

        @Test
        void commentBetweenSiblingsBlocksNeighborButNotEnd() {
            String source = "class A { void f() { foo(); /* note */ bar(); } }";
            LintNode bar = nodeOf("bar();", source);
            assertFalse(match(relational(RelationalMatcher.Op.FOLLOWS, pattern("foo();"),
                            StopBy.neighbor(), null), bar),
                    "the comment is the direct neighbor; relational traversal has no trivial skip");
            assertTrue(match(relational(RelationalMatcher.Op.FOLLOWS, pattern("foo();"),
                            StopBy.end(), null), bar),
                    "end still finds foo() past the comment");
        }

        @Test
        void firstSiblingHasNoPreviousAndLastHasNoNext() {
            LintNode foo = nodeOf("foo();", SEQ);
            assertFalse(match(relational(RelationalMatcher.Op.FOLLOWS, pattern("mid();"),
                    StopBy.end(), null), foo));
            LintNode bar = nodeOf("bar();", SEQ);
            assertFalse(match(relational(RelationalMatcher.Op.PRECEDES, pattern("mid();"),
                    StopBy.end(), null), bar));
        }

        @Test
        void fieldIsRejectedOnSiblingOperators() {
            NodeMatcher inner = pattern("foo();");
            assertThrows(NopLintException.class, () ->
                    new RelationalMatcher(RelationalMatcher.Op.FOLLOWS, inner, StopBy.end(), "body"));
            assertThrows(NopLintException.class, () ->
                    new RelationalMatcher(RelationalMatcher.Op.PRECEDES, inner, StopBy.end(), "body"));
        }
    }

    // ==================== environment contract ====================

    @Nested
    class EnvironmentContract {

        @Test
        void successfulRelationBindsCapturesIntoTheOuterEnv() {
            LintNode call = nodeOf("bar()",
                    "class A { void f() { try { bar(); } catch (Exception e) { baz(); } } }");
            MetaVarEnv env = new MetaVarEnv();
            assertTrue(relational(RelationalMatcher.Op.INSIDE, pattern("try { $$$ } catch (Exception $E) { $$$ }"),
                    StopBy.end(), null).matches(call, env));
            LintNode captured = env.getCapture("E");
            assertNotNull(captured, "the inner pattern's capture must flow into the outer env");
            assertEquals("e", captured.text(),
                    "the catch parameter's name identifier is bound to $E");
        }

        @Test
        void failedRelationLeavesTheEnvUntouched() {
            LintNode call = nodeOf("bar()",
                    "class A { void f() { try { bar(); } catch (Exception e) { baz(); } } }");
            MetaVarEnv env = new MetaVarEnv();
            assertFalse(relational(RelationalMatcher.Op.INSIDE, pattern("while ($C) { $$$ }"),
                    StopBy.end(), null).matches(call, env));
            assertTrue(env.singleCaptures().isEmpty() && env.multiCaptures().isEmpty(),
                    "a failed relation must not leak partial bindings");
        }

        @Test
        void repeatedCaptureConsistencyAppliesAcrossRelations() {
            String same = "class A { void f() { { foo(a, a); } } }";
            String different = "class A { void f() { { foo(a, b); } } }";
            LintNode sameBlock = nodeOf("{ foo(a, a); }", same);
            MetaVarEnv env = new MetaVarEnv();
            assertTrue(relational(RelationalMatcher.Op.HAS, pattern("foo($X, $X)"),
                    StopBy.end(), null).matches(sameBlock, env));
            assertNotNull(env.getCapture("X"),
                    "the relation's inner capture must reach the outer env");
            LintNode differentBlock = nodeOf("{ foo(a, b); }", different);
            assertFalse(relational(RelationalMatcher.Op.HAS, pattern("foo($X, $X)"),
                            StopBy.end(), null).matches(differentBlock, new MetaVarEnv()),
                    "the same-name consistency rule must hold inside the relation's probe path");
        }
    }

    // ==================== stopBy construction ====================

    @Nested
    class StopByConstruction {

        @Test
        void ruleModeWithoutStopMatcherIsRejected() {
            assertThrows(NopLintException.class, () -> StopBy.rule(null));
        }

        @Test
        void modesAreExposable() {
            assertEquals(StopBy.Mode.NEIGHBOR, StopBy.neighbor().mode());
            assertEquals(StopBy.Mode.END, StopBy.end().mode());
            assertEquals(StopBy.Mode.RULE, StopBy.rule(pattern("x")).mode());
        }
    }
}
