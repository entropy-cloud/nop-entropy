package io.nop.lint.core.type;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintNode;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focus tests for the L1 DeclTypeResolver contract (design 06 §5.2):
 * written-form fidelity for every declaration shape, explicit null failure
 * paths, and range() wiring back to the source bytes.
 */
class DeclTypeResolverTest {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private static DeclTypeResolver resolver;

    @BeforeAll
    static void setUp() {
        resolver = new DeclTypeResolver();
    }

    @Test
    void localVariableSimpleType() {
        LintNode decl = singleDecl("""
                class Demo {
                    void run() {
                        String s = "x";
                    }
                }
                """, "local_variable_declaration");
        assertEquals("String", resolver.resolveDeclType(decl));
    }

    @Test
    void localVariableGenericType() {
        LintNode decl = singleDecl("""
                class Demo {
                    void run() {
                        List<String> list = null;
                    }
                }
                """, "local_variable_declaration");
        assertEquals("List<String>", resolver.resolveDeclType(decl));
    }

    @Test
    void localVariableArrayType() {
        LintNode decl = singleDecl("""
                class Demo {
                    void run() {
                        String[] arr = null;
                    }
                }
                """, "local_variable_declaration");
        assertEquals("String[]", resolver.resolveDeclType(decl));
    }

    @Test
    void localVariableVarStaysLiteral() {
        LintNode decl = singleDecl("""
                class Demo {
                    void run() {
                        var count = 1;
                    }
                }
                """, "local_variable_declaration");
        assertEquals("var", resolver.resolveDeclType(decl),
                "var must stay the written text; initializer inference is L2 scope");
    }

    @Test
    void fieldDeclarationType() {
        LintNode decl = singleDecl("""
                class Demo {
                    private Map<String, Integer> cache;
                }
                """, "field_declaration");
        assertEquals("Map<String, Integer>", resolver.resolveDeclType(decl));
    }

    @Test
    void methodReturnTypes() {
        LintNode root = parse("""
                class Demo {
                    void a() {}
                    int b() { return 1; }
                    List<String> c() { return null; }
                }
                """);
        List<LintNode> methods = findAll(root, "method_declaration");
        assertEquals(3, methods.size());
        assertEquals("void", resolver.resolveDeclType(methods.get(0)));
        assertEquals("int", resolver.resolveDeclType(methods.get(1)));
        assertEquals("List<String>", resolver.resolveDeclType(methods.get(2)));
    }

    @Test
    void formalParameterTypes() {
        LintNode root = parse("""
                class Demo {
                    void run(List<String> xs, java.util.Map m, List<?> wild) {}
                }
                """);
        List<LintNode> params = findAll(root, "formal_parameter");
        assertEquals(3, params.size());
        assertEquals("List<String>", resolver.resolveDeclType(params.get(0)));
        assertEquals("java.util.Map", resolver.resolveDeclType(params.get(1)),
                "qualified names must be returned exactly as written");
        assertEquals("List<?>", resolver.resolveDeclType(params.get(2)),
                "wildcards must be returned exactly as written");
    }

    @Test
    void nonDeclarationNodeReturnsNull() {
        LintNode root = parse("""
                class Demo {
                    void run() {
                        f(1);
                    }
                    int f(int v) { return v; }
                }
                """);
        assertNull(resolver.resolveDeclType(findFirst(root, "block")));
        assertNull(resolver.resolveDeclType(findFirst(root, "expression_statement")));
        assertNull(resolver.resolveDeclType(findFirst(root, "method_invocation")));
        assertNull(resolver.resolveDeclType(root),
                "the program root is not a declaration");
    }

    @Test
    void declarationWithoutTypeFieldReturnsNull() {
        LintNode root = parse("""
                class Demo {
                    int f;
                    void g() {}
                }
                """);
        assertNull(resolver.resolveDeclType(findFirst(root, "class_declaration")),
                "class declarations carry a name, not a type field");
        assertNull(resolver.resolveDeclType(findFirst(root, "variable_declarator")),
                "the declarator sits under the typed declaration; it has no type field");
        assertNull(resolver.resolveDeclType(findFirst(root, "method_declaration").childByField("name")));
    }

    @Test
    void nullNodeReturnsNull() {
        assertNull(resolver.resolveDeclType(null));
    }

    @Test
    void resultSlicesMatchSourceBytes() {
        String source = """
                class Demo {
                    java.util.List<String> names;
                    int count(List<?> xs) {
                        var total = 0;
                        String[] parts = null;
                        return total;
                    }
                }
                """;
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        LintNode root = JAVA.parse(sourceBytes).root();

        List<LintNode> decls = new ArrayList<>();
        decls.add(findFirst(root, "field_declaration"));
        decls.add(findFirst(root, "method_declaration"));
        decls.addAll(findAll(root, "formal_parameter"));
        decls.addAll(findAll(root, "local_variable_declaration"));
        assertTrue(decls.size() >= 5, "snippet must contain all declaration shapes");

        for (LintNode decl : decls) {
            String resolved = resolver.resolveDeclType(decl);
            LintNode typeNode = decl.childByField("type");
            if (typeNode == null) {
                assertNull(resolved, "no type field must resolve to null at " + decl.kind());
                continue;
            }
            assertNotNull(resolved);
            String slice = new String(sourceBytes, typeNode.range().startByte(),
                    typeNode.range().length(), StandardCharsets.UTF_8);
            assertEquals(slice, resolved,
                    "resolver output must equal the independent source byte slice at " + decl.kind());
            assertTrue(source.contains(resolved),
                    "resolved text must occur verbatim in the source: " + resolved);
        }
    }

    private static LintNode parse(String source) {
        return JAVA.parse(source).root();
    }

    private static LintNode singleDecl(String source, String kind) {
        List<LintNode> all = findAll(parse(source), kind);
        assertEquals(1, all.size(), "snippet must contain exactly one " + kind);
        return all.get(0);
    }

    private static LintNode findFirst(LintNode root, String kind) {
        for (LintNode node : root) {
            if (node.kind().equals(kind)) {
                return node;
            }
        }
        throw new AssertionError("no " + kind + " in snippet tree");
    }

    private static List<LintNode> findAll(LintNode root, String kind) {
        List<LintNode> found = new ArrayList<>();
        for (LintNode node : root) {
            if (node.kind().equals(kind)) {
                found.add(node);
            }
        }
        return found;
    }
}
