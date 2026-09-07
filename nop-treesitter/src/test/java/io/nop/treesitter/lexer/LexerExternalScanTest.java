package io.nop.treesitter.lexer;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 wiring verification: the external-scan path is the path the corpus
 * runner and token tests exercise — the shipped JS blob's scanner program runs
 * inside {@link Lexer#next} and its tokens flow through the same
 * {@code GLRParser} shift machinery as internal tokens (no second scanner
 * implementation, no stubbed VM). The expected trees are cross-checked against
 * the C runtime's output for the same inputs.
 */
class LexerExternalScanTest {

    private static final Language JS =
            Language.fromClasspath("/grammars/javascript/tree-sitter-javascript-blob.bin");

    @Test
    void automaticSemicolonFlowsThroughTheShiftPath() {
        // C runtime: (program (expression_statement (identifier)) (expression_statement (identifier)))
        TSTree tree = TSParser.parse(JS, "a\nb");
        assertEquals("(program (expression_statement (identifier)) (expression_statement (identifier)))",
                tree.toSexpString(false));
    }

    @Test
    void templateCharsFlowThroughTheShiftPath() {
        // C runtime: (program (expression_statement (assignment_expression left: (identifier)
        // right: (template_string (string_fragment) (template_substitution (identifier))
        // (string_fragment)))))
        TSTree tree = TSParser.parse(JS, "x = `abc${y}def`");
        String sexp = tree.toSexpString(true);
        assertTrue(sexp.contains("(template_string (string_fragment) (template_substitution (identifier)) (string_fragment))"),
                sexp);
    }

    @Test
    void reservedWordsParseAsPropertyKeys() {
        // C runtime: property_identifier keys even though `if` / `let` are keywords
        TSTree tree = TSParser.parse(JS, "x = {if: 1, let: 2}");
        String sexp = tree.toSexpString(true);
        assertTrue(sexp.contains("(object (pair key: (property_identifier) value: (number))"
                + " (pair key: (property_identifier) value: (number)))"), sexp);
    }

    @Test
    void scannerRejectionFallsBackToTheInternalDfa() {
        // `;` has no external token: the scanner rejects and the DFA lexes it.
        TSTree tree = TSParser.parse(JS, "a;");
        assertEquals("(program (expression_statement (identifier)))", tree.toSexpString(false));
    }

    @Test
    void htmlCommentFlowThroughTheShiftPath() {
        // C runtime: the html_comment external token becomes a (html_comment) node
        TSTree tree = TSParser.parse(JS, "<!--comment-->");
        assertEquals("(program (html_comment))", tree.toSexpString(false));
    }
}