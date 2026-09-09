package io.nop.treesitter.parser.glr;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.parser.glr.ParserOptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: GLR + graph-structured stack — version forking on conflict groups,
 * prefer-shift option, alias relabeling on reduce, extras preserved on the GLR
 * path, and arena sanity on a stress fixture. The corpus-verified expectations
 * come from the vendored upstream Java corpus files.
 */
class GLRParserTest {

    private static final Language JSON = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
    private static final Language JAVA = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");

    @Test
    void jsonStaysByteExactOnGlrPath() {
        assertEquals("(document\n  (array\n    (number)\n    (number)))",
                TSParser.parse(JSON, "[1, 2]").toSExpression());
        assertEquals("(document\n  (object\n    (pair\n      (string\n        (string_content))\n"
                        + "      (number))\n    (comment)\n    (pair\n      (string\n        (string_content))\n"
                        + "      (string\n        (string_content)))))",
                TSParser.parse(JSON, "{ \"a\": 1, /*c*/ \"b\": \"2\" }").toSExpression());
    }

    @Test
    void jsonMultipleTopLevelObjectsSurviveVersionMerge() {
        assertEquals("(document\n  (object)\n  (object))", TSParser.parse(JSON, "{}\n{}").toSExpression());
        assertEquals("(document\n  (object)\n  (object)\n  (object))",
                TSParser.parse(JSON, "{}\n{}\n{}").toSExpression());
    }

    @Test
    void ambiguousGenericSnippetMatchesUpstreamTree() {
        String input = """
                class Box <T> {
                  private T theObject;
                  public Box( T arg) { theObject = arg; }
                  // more code
                }
                """;
        String expected = """
                (program
                  (class_declaration
                    (identifier)
                    (type_parameters
                      (type_parameter
                        (type_identifier)))
                    (class_body
                      (field_declaration
                        (modifiers)
                        (type_identifier)
                        (variable_declarator
                          (identifier)))
                      (constructor_declaration
                        (modifiers)
                        (identifier)
                        (formal_parameters
                          (formal_parameter
                            (type_identifier)
                            (identifier)))
                        (constructor_body
                          (expression_statement
                            (assignment_expression
                              (identifier)
                              (identifier)))))
                      (line_comment))))""";
        assertEquals(expected, TSParser.parse(JAVA, input).toSExpression());
    }

    @Test
    void aliasRelabelingAppliesTypeIdentifierInGenericContexts() {
        String tree = TSParser.parse(JAVA, "List<String> x = new ArrayList<String>();").toSExpression();
        assertTrue(tree.contains("(type_arguments\n        (type_identifier))"), tree);
        assertTrue(tree.contains("(generic_type\n      (type_identifier)"), tree);
    }

    @Test
    void forkOnConflictDropsInvalidVersionAndContinues() {
        // The member-access expression forks GLR versions; the versions that
        // cannot accept the identifier die and the viable ones carry the parse.
        String tree = TSParser.parse(JAVA,
                "class A { void m() { System.out.println(\"hi\"); } }").toSExpression();
        assertTrue(tree.contains("(method_invocation"), tree);
        assertTrue(tree.contains("(field_access"), tree);
    }

    @Test
    void preferShiftOptionStillParsesJsonAndJava() {
        ParserOptions preferShift = new ParserOptions(true);
        assertEquals("(document\n  (array\n    (number)\n    (number)))",
                TSParser.parse(JSON, "[1, 2]", preferShift).toSExpression());
        TSTree java = TSParser.parse(JAVA, "class A { int x = 1 + 2; }", preferShift);
        assertNotNull(java);
        assertEquals("(program\n  (class_declaration\n    (identifier)\n    (class_body\n"
                        + "      (field_declaration\n        (integral_type)\n"
                        + "        (variable_declarator\n          (identifier)\n"
                        + "          (binary_expression\n            (decimal_integer_literal)\n"
                        + "            (decimal_integer_literal)))))))",
                java.toSExpression());
    }

    @Test
    void extrasArePreservedThroughGlrPath() {
        String tree = TSParser.parse(JAVA, "class A { // trailing\n}").toSExpression();
        assertTrue(tree.contains("(line_comment)"), tree);
    }

    @Test
    void arenaGrowthStaysBoundedOnStressFixture() {
        // Concatenate a moderate Java file many times; GLR version reuse must
        // keep arena growth linear in the input size (no per-version blow-up).
        String unit = "class Box<T> {\n"
                + "  private T value;\n"
                + "  public Box(T v) { value = v; }\n"
                + "  public T get() { return value; }\n"
                + "}\n";
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            big.append("// unit ").append(i).append('\n').append(unit);
        }
        byte[] source = big.toString().getBytes(StandardCharsets.UTF_8);
        TSTree tree = TSParser.parse(JAVA, source);
        assertEquals("program", tree.language().symbolName(tree.arena().get(tree.root()).symbol()));
        int arenaSize = tree.arena().size();
        // ~1 node per token; tokens ≈ 1/4 of bytes. Bounds: linear, not exponential.
        assertTrue(arenaSize > source.length / 10, "arena too small: " + arenaSize);
        assertTrue(arenaSize < source.length * 4, "arena blow-up: " + arenaSize + " nodes for "
                + source.length + " bytes");
    }

    /**
     * Regression (roadmap item 10, JS full corpus): heavy keyword-capture
     * forking (every {@code await} here lexes as both identifier and keyword)
     * makes reduce()'s slice groups share versions whose slots die to mid-loop
     * merges; before the guard this crashed with arraycopy length -1 and lost
     * the parse. Found by "Reserved words as identifiers".
     */
    @Test
    void keywordForkedReducesSurviveMidLoopVersionRemoval() {
        Language js = Language.fromClasspath("/grammars/javascript/tree-sitter-javascript-blob.bin");
        TSTree tree = TSParser.parse(js,
                "function await(await) { await: await (await + await (0)); }");
        String sexp = tree.toSExpression();
        assertTrue(sexp.contains("function_declaration"), sexp);
        assertTrue(sexp.contains("statement_block"), sexp);
        assertTrue(sexp.contains("expression_statement"), sexp);
    }
}