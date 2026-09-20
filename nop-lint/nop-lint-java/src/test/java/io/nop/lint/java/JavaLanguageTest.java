package io.nop.lint.java;

import io.nop.lint.core.node.LintNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java binding focus tests: singleton identity, parse through the facade,
 * identity pattern preprocessing (the {@code $} identifier claim), kind
 * resolution (including the aliased and colliding kinds), and the end-to-end
 * blob → language → parse → LintNode chain.
 */
class JavaLanguageTest {

    private static final String SNIPPET = """
            class Demo {
                java.util.List<String> names;
                void run(int x) throws Exception {
                    if (x > 0) {
                        throw new RuntimeException("boom");
                    }
                }
            }
            """;

    @Test
    void singletonIsStable() {
        assertSame(JavaLanguage.get(), JavaLanguage.get());
        assertEquals("java", JavaLanguage.get().id());
    }

    @Test
    void parseProducesProgramRoot() {
        assertEquals("program", JavaLanguage.get().parse(SNIPPET).root().kind());
    }

    @Test
    void preprocessPatternIsIdentity() {
        String pattern = "throw new RuntimeException($$$ARGS)";
        assertEquals(pattern, JavaLanguage.get().preprocessPattern(pattern));
    }

    @Test
    void kindIdResolvesKnownKinds() {
        assertTrue(JavaLanguage.get().kindId("method_declaration") > 0);
        assertTrue(JavaLanguage.get().kindId("type_identifier") > 0,
                "aliased kind must resolve");
        assertTrue(JavaLanguage.get().kindId("not_a_kind") == -1);
    }

    @Test
    void metaVarTokensParseAsIdentifiers() {
        // The identity-expando claim: a pattern fragment with meta-vars parses
        // and the lexer keeps $VAR / $$$ARGS as single identifier tokens.
        LintNode root = JavaLanguage.get().parse("class A { void f() { int $VAR = 1; } }").root();
        LintNode field = findKind(root, "variable_declarator");
        assertNotNull(field, "$VAR declaration must parse");
        assertEquals("$VAR", field.childByField("name").text(),
                "lexer must keep the meta-var token intact");
    }

    @Test
    void endToEndTreeWalkFindsCoreKinds() {
        LintNode root = JavaLanguage.get().parse(SNIPPET).root();
        assertNotNull(findKind(root, "method_declaration"));
        assertNotNull(findKind(root, "throw_statement"));
        assertNotNull(findKind(root, "type_identifier"), "generic field type must surface");
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
