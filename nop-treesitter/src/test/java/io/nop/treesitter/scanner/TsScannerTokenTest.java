package io.nop.treesitter.scanner;

import io.nop.treesitter.codegen.ExtractedGrammar;
import io.nop.treesitter.codegen.ParserCExtractor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Token-for-token validation of the compiled TS/TSX scanner (common/scanner.h
 * hand translation) — the VM's external {@code (symbol, start, end)} must
 * equal the expectation derived from the scanner.c control flow for every
 * subroutine branch, with no tree-shape-only assertions.
 */
class TsScannerTokenTest {

    private static final Path PARSER_C = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-typescript/typescript/src/parser.c");
    private static final Path SCANNER_DSL = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-typescript/common/scanner.dsl");

    private static byte[] program;
    private static int[] sym;
    private static final int ASI = 0;
    private static final int TEMPLATE = 1;
    private static final int TERNARY = 2;
    private static final int HTML = 3;
    private static final int LOGICAL_OR = 4;
    private static final int ESCAPE = 5;
    private static final int REGEX = 6;
    private static final int JSX = 7;
    private static final int FUNC_SIG_ASI = 8;

    @BeforeAll
    static void compileTsScanner() throws Exception {
        String parserC = Files.readString(PARSER_C, StandardCharsets.UTF_8);
        ExtractedGrammar g = ParserCExtractor.extract(parserC);
        sym = g.externalScannerSymbolMap;
        String dsl = Files.readString(SCANNER_DSL, StandardCharsets.UTF_8);
        program = ScannerCompiler.compile(dsl, sym);
    }

    private static ScannerVM.Result run(String source, int position, int... validOrdinals) {
        boolean[] valid = new boolean[10];
        for (int o : validOrdinals) {
            valid[o] = true;
        }
        return ScannerVM.run(program, source.getBytes(StandardCharsets.UTF_8), position, valid);
    }

    // ------------------------------------------------------------------
    // scan_automatic_semicolon — same-line identifier/binary operator -> no ASI
    // ------------------------------------------------------------------

    @Test
    void noAsiBeforeIdentifierOnSameLine() {
        assertNull(run("x y", 1, ASI), "identifier on the same line never triggers ASI");
    }

    @Test
    void noAsiBeforeBinaryPlus() {
        assertNull(run("x\n+ y", 1, ASI), "binary plus on the next line does not trigger ASI");
    }

    @Test
    void asiAtEndOfInput() {
        ScannerVM.Result r = run("x", 1, ASI);
        assertEquals(sym[ASI], r.symbol());
        assertEquals(1, r.startOffset());
        assertEquals(1, r.endOffset(), "ASI is zero-width");
    }

    @Test
    void asiAtNewlineBeforeNextStatement() {
        ScannerVM.Result r = run("x\ny", 1, ASI);
        assertEquals(sym[ASI], r.symbol());
        assertEquals(1, r.startOffset(), "ASI is zero-width at the newline");
        assertEquals(1, r.endOffset());
    }

    @Test
    void asiBeforeUnaryPlusAfterNewline() {
        ScannerVM.Result r = run("x\n+ y", 1, ASI);
        assertNull(r, "'+' after a newline is a binary continuation only when followed by a non-plus");
    }

    @Test
    void asiBeforeDoublePlusAfterNewline() {
        ScannerVM.Result r = run("x\n++y", 1, ASI);
        assertEquals(sym[ASI], r.symbol(), "ASIs are inserted before ++");
    }

    @Test
    void noAsiBeforeInstanceof() {
        assertNull(run("x\ninstanceof Y", 1, ASI), "no ASI before the instanceof keyword");
    }

    @Test
    void asiBeforeIdentifierStartingWithIn() {
        ScannerVM.Result r = run("x\nindex", 1, ASI);
        assertEquals(sym[ASI], r.symbol(), "'index' is an identifier, not instanceof");
    }

    @Test
    void asiBeforeCloseBrace() {
        ScannerVM.Result r = run("}", 0, ASI);
        assertEquals(sym[ASI], r.symbol());
    }

    @Test
    void noAsiBeforeColonAfterBraceWithoutLogicalOr() {
        assertNull(run("} : y", 0, ASI), "object-pattern disambiguation: no ASI before ':' unless || is valid");
    }

    @Test
    void asiBeforeColonAfterBraceWithLogicalOr() {
        ScannerVM.Result r = run("} : y", 0, ASI, LOGICAL_OR);
        assertEquals(sym[ASI], r.symbol(), "ternary context: ASI fires before ':'");
    }

    @Test
    void asiFallsBackToTernaryOnQuestionMark() {
        ScannerVM.Result r = run("? a", 0, ASI, TERNARY);
        assertEquals(sym[TERNARY], r.symbol(), "ASI rejection at '?' falls back to the ternary scan");
        assertEquals(0, r.startOffset());
        assertEquals(1, r.endOffset());
    }

    @Test
    void asiFailsWithoutTernaryFallbackWhenCommentScanned() {
        assertNull(run("// c\n?", 0, ASI, TERNARY),
                "a scanned comment suppresses the ternary fallback (C scanned_comment)");
    }

    @Test
    void asiAfterLineComment() {
        ScannerVM.Result r = run("x\n// c\ny", 1, ASI);
        assertEquals(sym[ASI], r.symbol(), "a line comment between statements still yields ASI");
    }

    // ------------------------------------------------------------------
    // scan_ternary_qmark
    // ------------------------------------------------------------------

    @Test
    void ternaryBasicAccept() {
        ScannerVM.Result r = run("a ? b : c", 2, TERNARY);
        assertEquals(sym[TERNARY], r.symbol());
        assertEquals(2, r.startOffset());
        assertEquals(3, r.endOffset(), "the ternary token ends right after '?' (trailing ws excluded)");
    }

    @Test
    void noTernaryForOptionalChaining() {
        assertNull(run("a?.b", 1, TERNARY), "?. is optional chaining, not a ternary");
    }

    @Test
    void noTernaryForNullishCoalescing() {
        assertNull(run("a??b", 1, TERNARY), "?? is nullish coalescing, not a ternary");
    }

    @Test
    void noTernaryForOptionalParameter() {
        assertNull(run("f(a?: number)", 3, TERNARY), "?: is an optional parameter, not a ternary");
    }

    @Test
    void ternaryBeforeDecimalDigit() {
        ScannerVM.Result r = run("x ? .5 : 1", 2, TERNARY);
        assertEquals(sym[TERNARY], r.symbol(), "'?' followed by '.digit' is a ternary with a decimal literal");
    }

    // ------------------------------------------------------------------
    // scan_template_chars
    // ------------------------------------------------------------------

    @Test
    void templateCharsUpToSubstitution() {
        ScannerVM.Result r = run("abc${x}", 0, TEMPLATE);
        assertEquals(sym[TEMPLATE], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(3, r.endOffset());
    }

    @Test
    void templateCharsRejectAtBacktick() {
        assertNull(run("`", 0, TEMPLATE), "no content before the closing backtick");
    }

    // ------------------------------------------------------------------
    // scan_jsx_text
    // ------------------------------------------------------------------

    @Test
    void jsxTextUpToTag() {
        ScannerVM.Result r = run("<div>hello</div>", 5, JSX);
        assertEquals(sym[JSX], r.symbol());
        assertEquals(5, r.startOffset());
        assertEquals(10, r.endOffset());
    }

    @Test
    void jsxTextWhitespaceOnlyIsRejected() {
        assertNull(run("<div>\n  </div>", 5, JSX), "whitespace directly after a newline is not jsx_text");
    }

    // ------------------------------------------------------------------
    // scan_closing_comment
    // ------------------------------------------------------------------

    @Test
    void htmlCommentOpen() {
        ScannerVM.Result r = run("<!-- x\ny", 0, HTML);
        assertEquals(sym[HTML], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(6, r.endOffset());
    }

    @Test
    void htmlCommentClose() {
        // C consumes to end of line; "--> y" has no newline so the token spans the tail.
        ScannerVM.Result r = run("--> y", 0, HTML);
        assertEquals(sym[HTML], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(5, r.endOffset());
    }

    @Test
    void noHtmlCommentForOtherInput() {
        assertNull(run("x", 0, HTML));
    }

    // ------------------------------------------------------------------
    // function-signature ASI gate
    // ------------------------------------------------------------------

    @Test
    void functionSignatureAsiRejectsBeforeLbrace() {
        assertNull(run("{", 0, FUNC_SIG_ASI), "function-signature ASI is disabled before '{'");
    }

    @Test
    void functionSignatureAsiAcceptsAtEof() {
        // C-faithful: scan_automatic_semicolon assigns result_symbol =
        // AUTOMATIC_SEMICOLON unconditionally (scanner.h never emits the
        // function-signature token itself); the '{' gate only reads its validity.
        ScannerVM.Result r = run("", 0, FUNC_SIG_ASI);
        assertEquals(sym[ASI], r.symbol());
    }
}
