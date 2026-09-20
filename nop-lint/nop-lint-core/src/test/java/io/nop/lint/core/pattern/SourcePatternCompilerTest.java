package io.nop.lint.core.pattern;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.treesitter.language.Language;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiler focus tests over the real java grammar: flagship patterns, ERROR
 * wrapper stripping, missing-token policy, meta-var classification, rejection
 * paths, contextual compilation, kind precomputation.
 */
class SourcePatternCompilerTest {

    private static LintLanguage java;

    @BeforeAll
    static void loadLanguage() {
        java = new TreeSitterLanguageAdapter("java",
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    @Nested
    class MetaVarClassification {

        @Test
        void namedForms() {
            MetaVarSyntax.Spec single = MetaVarSyntax.parse("$VAR");
            assertEquals(MetaVarNode.Shape.SINGLE, single.shape());
            assertEquals("VAR", single.name());

            MetaVarSyntax.Spec drop = MetaVarSyntax.parse("$_VAR");
            assertEquals(MetaVarNode.Shape.DROP, drop.shape());
            assertEquals("_VAR", drop.name());

            MetaVarSyntax.Spec anonymous = MetaVarSyntax.parse("$$OP");
            assertEquals(MetaVarNode.Shape.ANONYMOUS, anonymous.shape());
            assertEquals("OP", anonymous.name());

            MetaVarSyntax.Spec multi = MetaVarSyntax.parse("$$$ARGS");
            assertEquals(MetaVarNode.Shape.MULTI, multi.shape());
            assertEquals("ARGS", multi.name());
        }

        @Test
        void bareFormsAreNonCapturing() {
            assertEquals(MetaVarNode.Shape.SINGLE, MetaVarSyntax.parse("$").shape());
            assertNull(MetaVarSyntax.parse("$").name());
            assertEquals(MetaVarNode.Shape.ANONYMOUS, MetaVarSyntax.parse("$$").shape());
            assertEquals(MetaVarNode.Shape.MULTI, MetaVarSyntax.parse("$$$").shape());
            assertEquals(MetaVarNode.Shape.DROP, MetaVarSyntax.parse("$_").shape());
        }

        @Test
        void invalidNamesFallBackToLiteral() {
            assertNull(MetaVarSyntax.parse("$1"), "digit start");
            assertNull(MetaVarSyntax.parse("$A$B"), "dollar inside name");
            assertNull(MetaVarSyntax.parse("$$$$VAR"), "four markers: $$$ + $VAR is invalid");
            assertNull(MetaVarSyntax.parse("$var"), "lowercase start is a literal");
            assertNull(MetaVarSyntax.parse("foo"), "no marker");
        }

        @Test
        void reservedFormsAreRejected() {
            assertThrows(NopLintException.class, () -> MetaVarSyntax.rejectReserved("$@Type"));
            assertThrows(NopLintException.class, () -> MetaVarSyntax.rejectReserved("$!null"));
        }

        @Test
        void captureSuppressionFollowsUnderscoreNames() {
            assertTrue(new MetaVarNode(MetaVarNode.Shape.SINGLE, "VAR", "$VAR").captures());
            assertFalse(new MetaVarNode(MetaVarNode.Shape.SINGLE, "_HINT", "$_HINT").captures());
            assertFalse(new MetaVarNode(MetaVarNode.Shape.MULTI, null, "$$$").captures());
        }
    }

    @Nested
    class FlagshipPatterns {

        @Test
        void throwPatternCompilesToThrowStatementRoot() {
            SourcePattern pattern = SourcePatternCompiler.compile(
                    "throw new RuntimeException($$$ARGS)", java);
            assertTrue(pattern.root() instanceof InternalNode root
                    && root.kindId() == java.kindId("throw_statement"),
                    "effective root must be throw_statement");
            InternalNode root = (InternalNode) pattern.root();
            MetaVarNode args = findMultiVar(root);
            assertNotNull(args, "argument list must contain $$$ARGS");
            assertEquals("ARGS", args.name());
            assertTrue(args.captures());
        }

        @Test
        void kindPrecomputeFiltersByRootKind() {
            SourcePattern pattern = SourcePatternCompiler.compile(
                    "throw new RuntimeException($$$ARGS)", java);
            assertEquals(1, pattern.possibleKindIds().length);
            assertEquals(java.kindId("throw_statement"), pattern.possibleKindIds()[0]);
            assertTrue(pattern.mayMatchKind(java.kindId("throw_statement")));
            assertFalse(pattern.mayMatchKind(java.kindId("method_invocation")));
        }

        @Test
        void sameNameOccurrencesCompileToSameCaptureName() {
            SourcePattern pattern = SourcePatternCompiler.compile("m($A, $A)", java);
            InternalNode root = (InternalNode) pattern.root();
            List<String> names = new ArrayList<>();
            collectSingleVarNames(root, names);
            assertEquals(List.of("A", "A"), names,
                    "each $A occurrence must compile to the same capture name");
        }

        @Test
        void equalityPatternKeepsTerminalOperator() {
            SourcePattern pattern = SourcePatternCompiler.compile("$A == $B", java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals(java.kindId("binary_expression"), root.kindId());
            assertEquals(3, root.children().size());
            assertEquals("A", metaVarOf(root.children().get(0)).name());
            assertEquals("==", ((TerminalNode) root.children().get(1)).text());
            assertEquals("B", metaVarOf(root.children().get(2)).name());
        }

        @Test
        void chainedInvocationPatternCompilesCleanly() {
            SourcePattern pattern = SourcePatternCompiler.compile(
                    "$OBJ.dao().$METHOD($$$ARGS)", java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals(java.kindId("method_invocation"), root.kindId());
            assertEquals(java.kindId("method_invocation"), pattern.possibleKindIds()[0]);
            assertNotNull(findMultiVar(root), "$$$ARGS must be captured");
        }

        @Test
        void metaVarOnlyPatternMatchesAnyKind() {
            SourcePattern pattern = SourcePatternCompiler.compile("$VAR", java);
            assertTrue(pattern.root() instanceof MetaVarNode);
            assertEquals(0, pattern.possibleKindIds().length);
            assertTrue(pattern.mayMatchKind(12345), "empty filter must not exclude");
        }

        @Test
        void kindIdRoundTripsThroughNameTable() {
            SourcePattern pattern = SourcePatternCompiler.compile(
                    "throw new RuntimeException($$$ARGS)", java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals(java.kindId("throw_statement"), root.kindId(),
                    "compiled kindId must equal language.kindId(kind name)");
        }
    }

    @Nested
    class ErrorWrapperStripping {

        @Test
        void bareExpressionIsStrippedToMethodInvocation() {
            SourcePattern pattern = SourcePatternCompiler.compile("foo()", java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals(java.kindId("method_invocation"), root.kindId());
        }

        @Test
        void binaryExpressionKeepsTerminalOperator() {
            SourcePattern pattern = SourcePatternCompiler.compile("$$L + $R", java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals(java.kindId("binary_expression"), root.kindId());
            assertEquals(3, root.children().size());
            MetaVarSyntax.Spec leftSpec = metaVarOf(root.children().get(0));
            assertEquals(MetaVarNode.Shape.ANONYMOUS, leftSpec.shape());
            TerminalNode op = (TerminalNode) root.children().get(1);
            assertEquals("+", op.text());
            MetaVarSyntax.Spec rightSpec = metaVarOf(root.children().get(2));
            assertEquals(MetaVarNode.Shape.SINGLE, rightSpec.shape());
        }

        @Test
        void classBodyBareMultiIsStripped() {
            SourcePattern pattern = SourcePatternCompiler.compile(
                    "class $C extends CrudBizModel { $$$ }", java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals(java.kindId("class_declaration"), root.kindId());
            assertTrue(findMultiVar(root) != null, "class body $$$ must survive as MULTI");
        }

        @Test
        void ifConditionDropVarAndBlockMulti() {
            SourcePattern pattern = SourcePatternCompiler.compile(
                    "if ($_COND) { $$$ }", java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals(java.kindId("if_statement"), root.kindId());
            MetaVarNode multi = findMultiVar(root);
            assertNotNull(multi, "block body $$$ must survive");
            assertFalse(multi.captures(), "bare $$$ never captures");
        }

        @Test
        void missingTrailingSemicolonIsDroppedNotBlocking() {
            // "throw ..." parses with a zero-width missing ';'; the effective
            // root must still be the throw_statement, and no zero-width
            // terminal may remain in the tree.
            SourcePattern pattern = SourcePatternCompiler.compile("return null", java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals(java.kindId("return_statement"), root.kindId());
            for (PatternNode child : root.children()) {
                assertFalse(child instanceof TerminalNode t && t.text().isEmpty(),
                        "zero-width missing token must be dropped");
            }
        }
    }

    @Nested
    class RejectionPaths {

        @Test
        void multiChildErrorIsRejected() {
            NopLintException e = assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.compile("throw new $$$", java));
            assertTrue(e.getMessage().contains("not valid code"), e.getMessage());
        }

        @Test
        void unparseablePatternIsRejected() {
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.compile("class {{{", java));
        }

        @Test
        void missingNamedNodeIsRejected() {
            // "void m(int) {}" recovers a zero-width missing identifier inside
            // formal_parameter — incomplete code, must fail fast.
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.compile("void m(int) {}", java));
        }

        @Test
        void reservedMetaVarFormsAreRejected() {
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.compile("$@Type", java));
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.compile("$!null", java));
        }

        @Test
        void blankAndBareMultiRootAreRejected() {
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.compile("", java));
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.compile("   ", java));
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.compile("$$$", java));
        }
    }

    @Nested
    class ContextualPatterns {

        private static final String SHELL =
                "class Demo { void target() { throw new RuntimeException(); } }";

        @Test
        void selectorPinsRootWithoutExtraction() {
            SourcePattern pattern = SourcePatternCompiler.contextual(
                    "method_declaration", SHELL, java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals(java.kindId("method_declaration"), root.kindId());
        }

        @Test
        void contextRootExcludedFromSelection() {
            // The context source always parses under a program root; selecting
            // "program" names the excluded root and must fail rather than
            // silently select it.
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.contextual("program", SHELL, java));
        }

        @Test
        void nonRootSelectorStillSelection() {
            // class_declaration sits below the program root, so it is a legal
            // selection target — pinning that the exclusion only covers the
            // root itself.
            SourcePattern pattern = SourcePatternCompiler.contextual(
                    "class_declaration", SHELL, java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals(java.kindId("class_declaration"), root.kindId());
        }

        @Test
        void unknownSelectorIsRejected() {
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.contextual("not_a_kind", SHELL, java));
        }

        @Test
        void blankArgumentsRejected() {
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.contextual("", SHELL, java));
            assertThrows(NopLintException.class,
                    () -> SourcePatternCompiler.contextual("method_declaration", "  ", java));
        }
    }

    @Nested
    class LeafAnchoredPatterns {

        @Test
        void numericLiteralIsInternalLeafRoot() {
            SourcePattern pattern = SourcePatternCompiler.compile("0", java);
            InternalNode root = (InternalNode) pattern.root();
            assertEquals("decimal_integer_literal", java.treeSitter().symbolName(root.kindId()));
            assertTrue(root.children().isEmpty(), "named language constructs end as internal leaves");
        }

        @Test
        void unnamedTokenPatternIsTerminalRoot() {
            SourcePattern pattern = SourcePatternCompiler.compile(";", java);
            assertTrue(pattern.root() instanceof TerminalNode terminal
                    && terminal.text().equals(";"));
            assertEquals(1, pattern.possibleKindIds().length);
        }
    }

    private static MetaVarSyntax.Spec metaVarOf(PatternNode node) {
        MetaVarNode metaVar = (MetaVarNode) node;
        return new MetaVarSyntax.Spec(metaVar.shape(), metaVar.name());
    }

    private static MetaVarNode findMultiVar(InternalNode root) {
        return findMultiVarIn(root, 0);
    }

    private static void collectSingleVarNames(PatternNode node, List<String> names) {
        if (node instanceof MetaVarNode metaVar && metaVar.shape() == MetaVarNode.Shape.SINGLE) {
            names.add(metaVar.name());
        } else if (node instanceof InternalNode internal) {
            for (PatternNode child : internal.children()) {
                collectSingleVarNames(child, names);
            }
        }
    }

    private static MetaVarNode findMultiVarIn(PatternNode node, int depth) {
        if (node instanceof MetaVarNode metaVar && metaVar.shape() == MetaVarNode.Shape.MULTI) {
            return metaVar;
        }
        if (node instanceof InternalNode internal && depth < 16) {
            for (PatternNode child : internal.children()) {
                MetaVarNode found = findMultiVarIn(child, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
