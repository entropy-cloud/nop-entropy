package io.nop.lint.js;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TSX binding matrix (plan item 19 Phase 1, Minimum Rules #25): the same
 * contract surface as {@link TypeScriptLanguageTest}, over the tsx grammar
 * blob. JSX-element parsing is pinned separately by the Phase 2 TSX
 * specific tests.
 */
class TsxLanguageTest {

    private static final String SNIPPET = """
            interface Props { label: string }
            const render = (p: Props): string => p.label;
            function run(items: string[]): void {
                for (const item of items) {
                    console.log(item);
                }
            }
            """;

    @Test
    void singletonIsStable() {
        assertSame(TsxLanguage.get(), TsxLanguage.get());
        assertEquals("tsx", TsxLanguage.get().id());
    }

    @Test
    void parseStringProducesProgramRoot() {
        assertEquals("program", TsxLanguage.get().parse(SNIPPET).root().kind());
    }

    @Test
    void parseBytesProducesProgramRoot() {
        LintTree tree = TsxLanguage.get().parse(SNIPPET.getBytes(StandardCharsets.UTF_8));
        assertEquals("program", tree.root().kind());
    }

    @Test
    void preprocessPatternIsIdentity() {
        assertEquals("console.log($$$ARGS)",
                TsxLanguage.get().preprocessPattern("console.log($$$ARGS)"));
        assertEquals("$A === $B",
                TsxLanguage.get().preprocessPattern("$A === $B"));
        assertEquals("$_X.setRequestHeader($A, $B)",
                TsxLanguage.get().preprocessPattern("$_X.setRequestHeader($A, $B)"));
    }

    @Test
    void metaVarTokensParseAsSingleIdentifiers() {
        LintNode root = TsxLanguage.get().parse("""
                function f($$$ARGS) {
                    let $VAR = 1;
                    let $_X = 2;
                }
                """).root();

        LintNode fn = findKind(root, "function_declaration");
        assertNotNull(fn, "$$$ARGS parameter list must parse");
        LintNode parameters = findKind(fn, "formal_parameters");
        assertNotNull(parameters);
        LintNode paramName = findKind(parameters, "identifier");
        assertEquals("$$$ARGS", paramName.text(),
                "the triple-dollar meta-var must lex as one identifier");

        LintNode declarator = findKind(root, "variable_declarator");
        assertNotNull(declarator, "$VAR declaration must parse");
        assertEquals("$VAR", declarator.childByField("name").text(),
                "the lexer must keep the meta-var token intact");
    }

    @Test
    void kindIdResolvesRepresentativeTsxKinds() {
        assertTrue(TsxLanguage.get().kindId("call_expression") > 0);
        assertTrue(TsxLanguage.get().kindId("member_expression") > 0);
        assertTrue(TsxLanguage.get().kindId("variable_declarator") > 0);
        assertTrue(TsxLanguage.get().kindId("function_declaration") > 0);
        assertTrue(TsxLanguage.get().kindId("jsx_element") > 0,
                "the JSX extension kinds must resolve on the tsx binding");
    }

    @Test
    void unknownKindAndErrorRecoveryKindResolveToMinusOne() {
        assertEquals(-1, TsxLanguage.get().kindId("not_a_kind"),
                "unknown kind names resolve to the explicit -1 contract");
        assertEquals(-1, TsxLanguage.get().kindId("ERROR"),
                "built-in recovery kinds are not grammar kinds");
    }

    @Test
    void parseIncrementalSmokeReusesTheAdapterContract() {
        LintTree oldTree = TsxLanguage.get().parse("const value = 1;");
        LintTree next = TsxLanguage.get().parseIncremental(oldTree,
                "const value = 42;".getBytes(StandardCharsets.UTF_8));
        assertEquals("program", next.root().kind(),
                "incremental parse satisfies the full-parse contract");
        assertNotNull(findKind(next.root(), "variable_declarator"));
    }

    @Test
    void suppressionProviderContractIsNull() {
        assertNull(TsxLanguage.get().suppressionProvider(),
                "TSX declares no annotation-carried suppression extractor in v1; "
                        + "linting runs the inline-comment suppression path");
    }

    @Test
    void endToEndTreeWalkFindsCoreKinds() {
        LintNode root = TsxLanguage.get().parse(SNIPPET).root();
        assertNotNull(findKind(root, "interface_declaration"));
        assertNotNull(findKind(root, "arrow_function"));
        assertNotNull(findKind(root, "call_expression"));
        assertNotNull(findKind(root, "member_expression"));
    }

    private LintNode findKind(LintNode root, String kind) {
        for (LintNode node : root) {
            if (node.kind().equals(kind)) {
                return node;
            }
        }
        return null;
    }
}
