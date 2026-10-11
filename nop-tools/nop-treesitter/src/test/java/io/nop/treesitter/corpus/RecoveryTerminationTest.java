package io.nop.treesitter.corpus;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Broken-input verification set for GLR error recovery across the six shipped
 * grammars (roadmap item 15c). Every expected tree was captured from the C
 * runtime oracles ({@code _tmp/ts-oracle/tsp*}, built from tree-sitter 0.25 +
 * the upstream grammars) or, where marked, is the recorded current shape of a
 * multi-round recovery whose tree still diverges from the C oracle — the same
 * watch-only class as the error-recovery plan's adjudicated sections (C trace
 * evidence in {@code ai-dev/logs/2026/09-11.md}). The zero-progress guard
 * ({@code zeroProgressRecoveryRounds}) must never fire for any input here:
 * a guard trip throws {@link io.nop.treesitter.TreeSitterException} and fails
 * the test.
 */
class RecoveryTerminationTest {

    private static final Map<String, String> C_ORACLE_TREES = new LinkedHashMap<>();
    private static final Map<String, String> ADJUDICATED_SHAPES = new LinkedHashMap<>();

    static {
        C_ORACLE_TREES.put("python|x = (1,\n",
                "(module (ERROR (identifier) (integer)))");
        C_ORACLE_TREES.put("python|def f():\n    if x:\n        pass\n      else:\n    y = 1\n",
                "(module (function_definition name: (identifier) parameters: (parameters) body: (block "
                        + "(if_statement condition: (identifier) consequence: (block (pass_statement)) "
                        + "alternative: (else_clause body: (block))) "
                        + "(assignment left: (identifier) right: (integer)))))");
        C_ORACLE_TREES.put("python|def a(b):\n    c.\n\n    \"\"\"\n    d\n    \"\"\"\n\n    e\n",
                "(module (function_definition name: (identifier) parameters: (parameters (identifier)) "
                        + "(ERROR (identifier)) body: (block (string (string_start) (string_content) "
                        + "(string_end)) (identifier))))");
        C_ORACLE_TREES.put("json|{\"a\": [1, 2",
                "(ERROR (string (string_content)) (number) (number))");
        C_ORACLE_TREES.put("java|class A { void m() {",
                "(program (class_declaration name: (identifier) body: (class_body "
                        + "(method_declaration type: (void_type) name: (identifier) parameters: "
                        + "(formal_parameters) body: (block (MISSING \"}\"))) (MISSING \"}\"))))");
        C_ORACLE_TREES.put("java|class { void m() }",
                "(program (ERROR type: (void_type) name: (identifier) parameters: (formal_parameters)))");
        C_ORACLE_TREES.put("javascript|let x = `abc",
                "(program (lexical_declaration (variable_declarator name: (identifier) (ERROR) "
                        + "value: (identifier))))");
        C_ORACLE_TREES.put("typescript|class A<T {",
                "(program (ERROR (identifier) (identifier)))");
        C_ORACLE_TREES.put("tsx|let x = <div",
                "(program (ERROR (identifier) (identifier)))");
        C_ORACLE_TREES.put("tsx|function f(): <T>(",
                "(program (ERROR (identifier) (formal_parameters) "
                        + "(type_parameters (type_parameter name: (type_identifier)))))");

        ADJUDICATED_SHAPES.put("json|{\"a\": 1,, \"b\": }",
                "(document (object (pair key: (string (string_content)) value: (number)) (ERROR) "
                        + "(pair key: (string (string_content)) value: (MISSING number))))");
        ADJUDICATED_SHAPES.put("python|for i in x:\n  break\n  else:\n",
                "(module (ERROR (identifier) (identifier) (break_statement) (identifier)))");
        ADJUDICATED_SHAPES.put("javascript|function f( { return 1;",
                "(program (ERROR (identifier) (ERROR (identifier)) (number)) (empty_statement))");
        ADJUDICATED_SHAPES.put("typescript|let x: = 1;",
                "(program (lexical_declaration (variable_declarator name: (identifier) "
                        + "type: (type_annotation (MISSING type_identifier)) value: (number))))");
        ADJUDICATED_SHAPES.put("typescript|class Broken {\n    method( {\n        const x = ;\n"
                + "    }\n}\nif (x { y(); }\n",
                "(program (class_declaration name: (type_identifier) body: (class_body "
                        + "(ERROR (property_identifier) pattern: (object_pattern (ERROR (identifier)) "
                        + "(shorthand_property_identifier_pattern) (ERROR))))) "
                        + "(if_statement condition: (parenthesized_expression (identifier) "
                        + "(MISSING \")\")) consequence: (statement_block "
                        + "(expression_statement (call_expression function: (identifier) "
                        + "arguments: (arguments))))))");
    }

    private TSTree parse(String grammar, String source) {
        Language language = Language.fromClasspath("/grammars/" + grammar + "/tree-sitter-" + grammar + "-blob.bin");
        if (grammar.equals("python")) {
            language.setExternalScannerFactory(io.nop.treesitter.scanner.PythonScanner::new);
        }
        return TSParser.parse(language, source.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void brokenInputsRecoverToTheCOracleTrees() {
        C_ORACLE_TREES.forEach((key, expected) -> {
            int sep = key.indexOf('|');
            String grammar = key.substring(0, sep);
            String source = key.substring(sep + 1);
            assertEquals(expected, parse(grammar, source).toSexpString(true),
                    grammar + " source: " + source.replace("\n", "\\n"));
        });
    }

    @Test
    void adjudicatedMultiRoundRecoveriesTerminateWithRecordedShapes() {
        ADJUDICATED_SHAPES.forEach((key, expected) -> {
            int sep = key.indexOf('|');
            String grammar = key.substring(0, sep);
            String source = key.substring(sep + 1);
            assertEquals(expected, parse(grammar, source).toSexpString(true),
                    grammar + " source: " + source.replace("\n", "\\n"));
        });
    }

    @Test
    void noRecoveryRoundsTripTheZeroProgressGuard() {
        Map<String, String> all = new LinkedHashMap<>(C_ORACLE_TREES);
        all.putAll(ADJUDICATED_SHAPES);
        all.forEach((key, ignored) -> {
            int sep = key.indexOf('|');
            String grammar = key.substring(0, sep);
            String source = key.substring(sep + 1);
            try {
                parse(grammar, source);
            } catch (io.nop.treesitter.TreeSitterException e) {
                if (String.valueOf(e).contains("no progress")) {
                    org.junit.jupiter.api.Assertions.fail(
                            "zero-progress guard tripped for " + grammar + " source: " + source, e);
                }
            }
        });
    }
}
