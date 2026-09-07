package io.nop.treesitter.scanner;

import io.nop.treesitter.codegen.ExtractedGrammar;
import io.nop.treesitter.codegen.ParserCExtractor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Phase 3: the compiled JS scanner program, cross-checked against the real C
 * {@code scanner.c}. The expectation table below was produced by compiling the
 * vendored tree-sitter-javascript {@code scanner.c} with a TSLexer harness
 * (the C runtime's token-start/mark-end/finish-clamp semantics) and running
 * both implementations over the same 73-case battery — every case matches
 * token-for-token (external ordinal, start, end).
 */
class JsScannerCCrossCheckTest {

    private static final Path PARSER_C = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-javascript/src/parser.c");
    private static final Path SCANNER_DSL = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-javascript/src/scanner.dsl");

    @Test
    void vmMatchesCompiledCScanner() throws Exception {
        String parserC = Files.readString(PARSER_C, StandardCharsets.UTF_8);
        ExtractedGrammar g = ParserCExtractor.extract(parserC);
        int[] sym = g.externalScannerSymbolMap;
        String dsl = Files.readString(SCANNER_DSL, StandardCharsets.UTF_8);
        byte[] program = ScannerCompiler.compile(dsl, sym);

        expect(program, sym, "templ1", "abc`def", new int[]{1, 0, 3}, 1);
        expect(program, sym, "templ2", "abc${x}", new int[]{1, 0, 3}, 1);
        expect(program, sym, "templ3", "${}", null, 1);
        expect(program, sym, "templ4", "$`x", new int[]{1, 0, 1}, 1);
        expect(program, sym, "templ5", "abc", null, 1);
        expect(program, sym, "templ6", "\\`", null, 1);
        expect(program, sym, "templ7", "a\\`", new int[]{1, 0, 1}, 1);
        expect(program, sym, "templ8", "a$b`", new int[]{1, 0, 3}, 1);
        expect(program, sym, "templ9", "a$`", new int[]{1, 0, 2}, 1);
        expect(program, sym, "jsx1", "hello<", new int[]{7, 0, 5}, 7);
        expect(program, sym, "jsx2", "  ", new int[]{7, 0, 2}, 7);
        expect(program, sym, "jsx3", "\n  ", null, 7);
        expect(program, sym, "jsx4", "\nX", new int[]{7, 0, 2}, 7);
        expect(program, sym, "jsx5", "&", null, 7);
        expect(program, sym, "jsx6", "a\nb", new int[]{7, 0, 3}, 7);
        expect(program, sym, "jsx7", "\n \n X", new int[]{7, 0, 5}, 7);
        expect(program, sym, "jsx8", "x{", new int[]{7, 0, 1}, 7);
        expect(program, sym, "asi1", "abc", null, 0);
        expect(program, sym, "asi2", "\n", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi3", "\n}", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi4", "\n.5", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi5", "\n.x", null, 0);
        expect(program, sym, "asi6", "\n++", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi7", "\n+=", null, 0);
        expect(program, sym, "asi8", "\n!a", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi9", "\n!=", null, 0);
        expect(program, sym, "asi10", "\nin", null, 0);
        expect(program, sym, "asi11", "\nin ", null, 0);
        expect(program, sym, "asi12", "\ninX", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi13", "\ninstanceof ", null, 0);
        expect(program, sym, "asi14", "\ninstanceofX", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi15", "/*c*/", null, 0);
        expect(program, sym, "asi16", "/*\n*/x", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi17", "/", null, 0);
        expect(program, sym, "asi18", "//line", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi19", "\n;", null, 0);
        expect(program, sym, "asi20", "\n/", null, 0);
        expect(program, sym, "asi21", "\n?", null, 0);
        expect(program, sym, "asi22", "\n--", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi23", "\n-", null, 0);
        expect(program, sym, "asi24", "//a\nb", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi25", "/*a*/\nb", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi26", "/*a*/b", null, 0);
        expect(program, sym, "asi27", "\n \n.", null, 0);
        expect(program, sym, "asi28", "/*\n*/", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi29", "/*a\nb*/c", new int[]{0, 0, 0}, 0);
        expect(program, sym, "asi30", "\n2028", new int[]{0, 0, 0}, 0);
        expect(program, sym, "tern1", "?a", new int[]{2, 0, 1}, 2);
        expect(program, sym, "tern2", "??", null, 2);
        expect(program, sym, "tern3", "?.5", new int[]{2, 0, 1}, 2);
        expect(program, sym, "tern4", "?.", null, 2);
        expect(program, sym, "tern5", " ?", new int[]{2, 1, 2}, 2);
        expect(program, sym, "tern6", "x", null, 2);
        expect(program, sym, "tern7", "??a", null, 2);
        expect(program, sym, "html1", "<!--hi", new int[]{3, 0, 6}, 3);
        expect(program, sym, "html2", "  <!--x", new int[]{3, 2, 7}, 3);
        expect(program, sym, "html3", "-->x", new int[]{3, 0, 4}, 3);
        expect(program, sym, "html4", "<!-x", null, 3);
        expect(program, sym, "html5", "x", null, 3);
        expect(program, sym, "html6", "<!--x-->", new int[]{3, 0, 8}, 3);
        expect(program, sym, "html7", " <!--\n", new int[]{3, 1, 5}, 3);
        expect(program, sym, "gate1", "abc`", null, 1, 0);
        expect(program, sym, "gate2", "<!--x", null, 4);
        expect(program, sym, "gate3", "?x", null, 5);
        expect(program, sym, "gate4", "?x", null, 6);
        expect(program, sym, "gate5", "?", new int[]{2, 0, 1}, 0, 2);
        expect(program, sym, "gate6", "/*x*/?", null, 0, 2);
        expect(program, sym, "gate7", "x", null, 0);
        expect(program, sym, "gate8", "x", null, 0, 2);
        expect(program, sym, "gate9", "?x", new int[]{2, 0, 1}, 0, 2);
        expect(program, sym, "gate10", "<!--x", null, 3, 4);
        expect(program, sym, "gate11", "\n  ", null, 7);
        expect(program, sym, "gate12", "abc`", new int[]{1, 0, 3}, 1, 7);
    }

    private static void expect(byte[] program, int[] sym, String name, String source,
                               int[] expected, int... ordinals) {
        boolean[] valid = new boolean[8];
        for (int o : ordinals) {
            valid[o] = true;
        }
        ScannerVM.Result r = ScannerVM.run(program, source.getBytes(StandardCharsets.UTF_8), 0, valid);
        if (expected == null) {
            assertNull(r, name + ": expected no token");
            return;
        }
        if (r == null) {
            throw new AssertionError(name + ": expected token but got none");
        }
        int symbolId = sym[expected[0]];
        assertArrayEquals(new int[]{symbolId, expected[1], expected[2]},
                new int[]{r.symbol(), r.startOffset(), r.endOffset()},
                name + ": token (symbol/start/end) must match the C scanner");
    }
}