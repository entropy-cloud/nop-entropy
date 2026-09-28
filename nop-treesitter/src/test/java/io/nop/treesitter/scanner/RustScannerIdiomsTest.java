package io.nop.treesitter.scanner;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.compat.TreeSitterRust;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RustScanner fidelity spot-checks (plan 2026-09-28-2400-1 G4): token-level
 * idioms from the C scanner, each against the tree shape the C runtime
 * produces (oracle-verified where the input is valid rust).
 */
class RustScannerIdiomsTest {

    private String sexp(String source) {
        LanguageHolder.h.language().setExternalScannerFactory(RustScanner::new);
        TSTree tree = TSParser.parse(LanguageHolder.h.language(), source.getBytes(StandardCharsets.UTF_8));
        return tree.toSexpString(false);
    }

    /** Lazily loads the language once (the blob wires the factory statically). */
    private static class LanguageHolder {
        static final TreeSitterRust h = new TreeSitterRust();
    }

    @Test
    void rawStringsWithHashCounts() {
        String tree = sexp("let a = r#\"raw \" content\"#;");
        assertTrue(tree.contains("raw_string_literal"), "raw string must be parsed as raw: " + tree);
        assertFalse(tree.contains("ERROR"), "balanced hashes must not error: " + tree);
    }

    @Test
    void rawStringPrefixVariants() {
        for (String src : new String[]{"let a = br#\"x\"#;", "let a = cr#\"x\"#;"}) {
            String tree = sexp(src);
            assertTrue(tree.contains("raw_string_literal"),
                    "b/c prefixed raw string must parse: " + src + " -> " + tree);
        }
    }

    @Test
    void floatLiteralBranches() {
        // with fraction/exponent → float_literal; the C scanner rejects bare
        // `1f64` (no fraction/exponent), and `1.max(2)` is not a float.
        assertTrue(sexp("let a = 1.0;").contains("float_literal"));
        assertTrue(sexp("let a = 1.0e5;").contains("float_literal"));
        assertTrue(sexp("let a = 123.0f64;").contains("float_literal"));
        assertTrue(sexp("let a = 12E+99_f64;").contains("float_literal"));
        String methodCall = sexp("let a = 1.max(2);");
        assertFalse(methodCall.contains("float_literal"),
                "1.max(2) must not lex a float (dot followed by letter): " + methodCall);
        String range = sexp("let a = 1..2;");
        assertFalse(range.contains("float_literal"),
                "1..2 must not lex a float (range dot): " + range);
    }

    @Test
    void nestedBlockComments() {
        String tree = sexp("/* outer /* nested */ still */ fn f() {}");
        assertTrue(tree.contains("block_comment"));
        assertFalse(tree.contains("ERROR"), "nested block comment must consume to the outer close: " + tree);
    }

    @Test
    void docCommentsCarryMarkersAndContent() {
        String tree = sexp("/// outer doc\nfn f() {}");
        assertTrue(tree.contains("outer_doc_comment_marker"), tree);
        assertTrue(tree.contains("doc_comment"), tree);

        String inner = sexp("//! inner doc\n");
        assertTrue(inner.contains("inner_doc_comment_marker"), inner);
        assertTrue(inner.contains("doc_comment"), inner);
    }

    @Test
    void plainStringsKeepStringContentExternal() {
        String tree = sexp("let a = \"hi \\\" there\";");
        assertTrue(tree.contains("string_literal"), tree);
        assertFalse(tree.contains("ERROR"), "escaped quote must stay inside the string: " + tree);
    }
}
