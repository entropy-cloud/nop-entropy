package io.nop.treesitter.corpus;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Proof for the per-version external-scanner state (roadmap item 15b): the
 * python indentation scanner's serialized state must be tracked per GLR stack
 * version — across pattern/expression and print/call forks that span newline
 * handling, and across the error-recovery discontinuity — or the token stream
 * downstream of the fork/recovery corrupts the block structure. Every expected
 * tree was captured from the C oracle ({@code _tmp/ts-oracle/tsp}) with fields
 * enabled, byte for byte.
 */
class PythonScannerStateIsolationTest {

    private static Language language;

    @BeforeAll
    static void load() {
        language = Language.fromClasspath("/grammars/python/tree-sitter-python-blob.bin");
        language.setExternalScannerFactory(io.nop.treesitter.scanner.PythonScanner::new);
    }

    private static String parse(String source) {
        TSTree tree = TSParser.parse(language, source.getBytes(StandardCharsets.UTF_8));
        return tree.toSexpString(true);
    }

    private static TSNode find(TSNode node, String type) {
        if (node.type().equals(type)) {
            return node;
        }
        for (int i = 0; i < node.namedChildCount(); i++) {
            TSNode found = find(node.namedChild(i), type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void forkedVersionsKeepTheirOwnScannerStateAcrossNewlines() {
        assertEquals("(module (call function: (identifier) arguments: (argument_list "
                + "(list_splat (identifier)) (dictionary_splat (identifier)))) "
                + "(if_statement condition: (identifier) consequence: (block (pass_statement))))",
                parse("print(*a,\n      **b)\nif x:\n    pass\n"));
        assertEquals("(module (match_statement subject: (identifier) body: (block alternative: "
                + "(case_clause (case_pattern (list_pattern (case_pattern (integer)) "
                + "(case_pattern (splat_pattern (identifier))))) consequence: "
                + "(block (assignment left: (identifier) right: (identifier)))))))",
                parse("match x:\n    case [1, *rest]:\n        y = rest\n"));
    }

    @Test
    void scannerStateSurvivesTheRecoveryDiscontinuity() {
        assertEquals("(module (function_definition name: (identifier) parameters: (parameters) "
                + "body: (block (return_statement (tuple (integer) (integer))))))",
                parse("def f():\n    return (1,\n2)\n"));
    }

    private static String describe(TSNode node) {
        StringBuilder sb = new StringBuilder(node.type()).append('(');
        for (int i = 0; i < node.namedChildCount(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(describe(node.namedChild(i)));
        }
        return sb.append(')').toString();
    }

    @Test
    void postRecoverySubtreeEqualsTheRepairedSourceSubtree() {
        TSTree broken = TSParser.parse(language, "def f():\n    return (1,\n2)\n"
                .getBytes(StandardCharsets.UTF_8));
        TSTree repaired = TSParser.parse(language, "def f():\n    return (1, 2)\n"
                .getBytes(StandardCharsets.UTF_8));
        TSNode brokenReturn = find(broken.rootNode(), "return_statement");
        TSNode repairedReturn = find(repaired.rootNode(), "return_statement");
        assertNotNull(brokenReturn);
        assertNotNull(repairedReturn);
        assertEquals(describe(repairedReturn), describe(brokenReturn));
    }
}
