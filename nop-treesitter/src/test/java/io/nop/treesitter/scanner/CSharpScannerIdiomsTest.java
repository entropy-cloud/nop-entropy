package io.nop.treesitter.scanner;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.compat.TreeSitterCSharp;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSharpScanner fidelity spot-checks (plan 2026-09-29-0100-1 G4): interpolation
 * string kinds, raw strings, OPT_SEMI and the simple-lambda speculative scan.
 */
class CSharpScannerIdiomsTest {

    private String sexp(String source) {
        TSTree tree = TSParser.parse(LanguageHolder.h.language(), source.getBytes(StandardCharsets.UTF_8));
        return tree.toSexpString(false);
    }

    private static class LanguageHolder {
        static final TreeSitterCSharp h = new TreeSitterCSharp();
    }

    @Test
    void regularInterpolatedString() {
        String tree = sexp("var s = $\"x {y} z\";");
        assertTrue(tree.contains("interpolated_string_expression"), tree);
        assertTrue(tree.contains("interpolation"), tree);
        assertFalse(tree.contains("ERROR"), tree);
    }

    @Test
    void verbatimInterpolatedString() {
        String tree = sexp("var s = $@\"a {y} b\";");
        assertTrue(tree.contains("interpolated_string_expression"), tree);
        assertFalse(tree.contains("ERROR"), tree);
    }

    @Test
    void doubledDollarEscape() {
        String tree = sexp("var s = $$\"{{x}}\";");
        assertTrue(tree.contains("interpolated_string_expression"), tree);
        assertFalse(tree.contains("ERROR"), "doubled braces must escape, not open an interpolation: " + tree);
    }

    @Test
    void rawStringTripleQuote() {
        String tree = sexp("var s = \"\"\"raw \" content\"\"\";");
        assertTrue(tree.contains("raw_string_literal"), tree);
        assertFalse(tree.contains("ERROR"), tree);
    }

    @Test
    void simpleLambdaParenNotMisfired() {
        String tree = sexp("var f = (x, y) => x + y;");
        assertFalse(tree.contains("ERROR"), "plain (x, y) => must not be touched by the lambda scan: " + tree);
    }

    @Test
    void scopedLambdaParenFires() {
        String tree = sexp("var f = (ref x) => x;");
        assertFalse(tree.contains("ERROR"), "(ref x) => must parse: " + tree);
    }
}
