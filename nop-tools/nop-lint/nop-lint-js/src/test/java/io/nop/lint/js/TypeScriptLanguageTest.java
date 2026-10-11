package io.nop.lint.js;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TypeScript binding matrix (plan item 19 Phase 1, Minimum Rules #25):
 * singleton identity, both parse entries, identity pattern preprocessing
 * (the {@code $} identifier claim, pinned by parse-level assertions), kind
 * resolution (representative TS kinds, unknown kind {@code -1}, the ERROR
 * recovery kind {@code -1}), the incremental parse smoke through the shared
 * adapter contract, the null suppression-provider contract, and the
 * end-to-end blob → binding → parse → LintNode chain.
 */
class TypeScriptLanguageTest {

    private static final String SNIPPET = """
            interface User { name: string }
            enum Color { Red }
            const greet = (u: User): string => `hi ${u.name}`;
            function run(x: number): void {
                if (x > 0) {
                    console.log(x);
                }
            }
            """;

    @Test
    void singletonIsStable() {
        assertSame(TypeScriptLanguage.get(), TypeScriptLanguage.get());
        assertEquals("typescript", TypeScriptLanguage.get().id());
    }

    @Test
    void parseStringProducesProgramRoot() {
        assertEquals("program", TypeScriptLanguage.get().parse(SNIPPET).root().kind());
    }

    @Test
    void parseBytesProducesProgramRoot() {
        LintTree tree = TypeScriptLanguage.get().parse(SNIPPET.getBytes(StandardCharsets.UTF_8));
        assertEquals("program", tree.root().kind());
    }

    @Test
    void preprocessPatternIsIdentity() {
        assertEquals("console.log($$$ARGS)",
                TypeScriptLanguage.get().preprocessPattern("console.log($$$ARGS)"));
        assertEquals("$A == $B",
                TypeScriptLanguage.get().preprocessPattern("$A == $B"));
        assertEquals("if ($_COND) { $$$ }",
                TypeScriptLanguage.get().preprocessPattern("if ($_COND) { $$$ }"));
    }

    @Test
    void metaVarTokensParseAsSingleIdentifiers() {
        // The identity-expando claim pinned at parse level: the TS grammar's
        // lexer keeps $VAR / $$$ARGS / $_X intact as one identifier token.
        LintNode root = TypeScriptLanguage.get().parse("""
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

        assertNotEquals(-1, TypeScriptLanguage.get().kindId("variable_declarator"));
    }

    @Test
    void kindIdResolvesRepresentativeTsKinds() {
        assertTrue(TypeScriptLanguage.get().kindId("call_expression") > 0);
        assertTrue(TypeScriptLanguage.get().kindId("member_expression") > 0);
        assertTrue(TypeScriptLanguage.get().kindId("variable_declarator") > 0);
        assertTrue(TypeScriptLanguage.get().kindId("function_declaration") > 0);
        assertTrue(TypeScriptLanguage.get().kindId("enum_declaration") > 0,
                "TS-specific declaration kind must resolve");
        assertTrue(TypeScriptLanguage.get().kindId("interface_declaration") > 0);
    }

    @Test
    void unknownKindAndErrorRecoveryKindResolveToMinusOne() {
        assertEquals(-1, TypeScriptLanguage.get().kindId("not_a_kind"),
                "unknown kind names resolve to the explicit -1 contract");
        assertEquals(-1, TypeScriptLanguage.get().kindId("ERROR"),
                "built-in recovery kinds are not grammar kinds");
    }

    @Test
    void parseIncrementalSmokeReusesTheAdapterContract() {
        LintTree oldTree = TypeScriptLanguage.get().parse("const value = 1;");
        LintTree next = TypeScriptLanguage.get().parseIncremental(oldTree,
                "const value = 42;".getBytes(StandardCharsets.UTF_8));
        assertEquals("program", next.root().kind(),
                "incremental parse satisfies the full-parse contract");
        assertNotNull(findKind(next.root(), "variable_declarator"));
    }

    @Test
    void suppressionProviderContractIsNull() {
        assertNull(TypeScriptLanguage.get().suppressionProvider(),
                "TS declares no annotation-carried suppression extractor in v1; "
                        + "linting runs the inline-comment suppression path");
    }

    @Test
    void endToEndTreeWalkFindsCoreKinds() {
        LintNode root = TypeScriptLanguage.get().parse(SNIPPET).root();
        assertNotNull(findKind(root, "interface_declaration"));
        assertNotNull(findKind(root, "enum_declaration"));
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
