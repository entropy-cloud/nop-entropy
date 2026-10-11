package io.nop.treesitter.corpus;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks the python pattern/expression GLR fork outputs to the C runtime
 * (tree-sitter 0.25 + tree-sitter-python 0.23.4, oracle built from
 * {@code ~/sources/treesitter}): every expected tree below was captured from
 * the C oracle with fields enabled, byte for byte. Guards the GSS pop-path
 * enumeration order (C {@code stack__iter} port) and the sequential reduce
 * slice handling that the {@code print(d, *e)} variant-selection fix landed.
 */
class PythonSplatVariantTest {

    private static Language language;

    @BeforeAll
    static void load() {
        language = Language.fromClasspath("/grammars/python/tree-sitter-python-blob.bin");
        language.setExternalScannerFactory(io.nop.treesitter.scanner.PythonScanner::new);
    }

    private String parse(String source) {
        TSTree tree = TSParser.parse(language, source.getBytes(StandardCharsets.UTF_8));
        return tree.toSexpString(true);
    }

    @Test
    void splatFamilyMatchesCOracle() {
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("a, *b.c = d\n",
                "(module (assignment left: (pattern_list (identifier) (list_splat_pattern "
                        + "(attribute object: (identifier) attribute: (identifier)))) right: (identifier)))");
        cases.put("*b.c = d\n",
                "(module (assignment left: (attribute object: (list_splat (identifier)) "
                        + "attribute: (identifier)) right: (identifier)))");
        cases.put("a, *b.c, *d = e\n",
                "(module (assignment left: (pattern_list (identifier) (list_splat_pattern "
                        + "(attribute object: (identifier) attribute: (identifier))) "
                        + "(list_splat_pattern (identifier))) right: (identifier)))");
        cases.put("a, *b = d\n",
                "(module (assignment left: (pattern_list (identifier) (list_splat_pattern (identifier))) "
                        + "right: (identifier)))");
        cases.put("x = *b.c\n",
                "(module (assignment left: (identifier) right: (list_splat "
                        + "(attribute object: (identifier) attribute: (identifier)))))");
        cases.put("a, *b[0], c = d\n",
                "(module (assignment left: (pattern_list (identifier) (list_splat_pattern "
                        + "(subscript value: (identifier) subscript: (integer))) (identifier)) "
                        + "right: (identifier)))");
        cases.put("f(*args, **kw)\n",
                "(module (call function: (identifier) arguments: (argument_list "
                        + "(list_splat (identifier)) (dictionary_splat (identifier)))))");
        cases.forEach((source, expected) -> assertEquals(expected, parse(source),
                "source: " + source.replace("\n", "\\n")));
    }

    @Test
    void callArgumentSplatSurvivesReduceSliceSelection() {
        assertEquals("(module (call function: (identifier) arguments: (argument_list "
                + "(identifier) (list_splat (identifier)))))", parse("print(d, *e)\n"));
    }
}
