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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: token-for-token validation of the compiled JS scanner — the VM's
 * external {@code (symbol, start, end)} must equal the expectation derived from
 * the scanner.c control flow for every scan function and dispatcher gate, with
 * no tree-shape-only assertions.
 */
class JsScannerTokenTest {

    private static final Path PARSER_C = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-javascript/src/parser.c");
    private static final Path SCANNER_DSL = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-javascript/src/scanner.dsl");

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

    @BeforeAll
    static void compileJsScanner() throws Exception {
        String parserC = Files.readString(PARSER_C, StandardCharsets.UTF_8);
        ExtractedGrammar g = ParserCExtractor.extract(parserC);
        sym = g.externalScannerSymbolMap;
        String dsl = Files.readString(SCANNER_DSL, StandardCharsets.UTF_8);
        program = ScannerCompiler.compile(dsl, sym);
        assertTrue(program.length > 0);
    }

    private static ScannerVM.Result run(String source, int position, int... validOrdinals) {
        boolean[] valid = new boolean[8];
        for (int o : validOrdinals) {
            valid[o] = true;
        }
        return ScannerVM.run(program, source.getBytes(StandardCharsets.UTF_8), position, valid);
    }

    // ------------------------------------------------------------------
    // scan_template_chars
    // ------------------------------------------------------------------

    @Test
    void templateCharsUpToClosingBacktick() {
        ScannerVM.Result r = run("abc`def", 0, TEMPLATE);
        assertEquals(sym[TEMPLATE], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(3, r.endOffset());
    }

    @Test
    void templateCharsUpToSubstitution() {
        ScannerVM.Result r = run("abc${x}", 0, TEMPLATE);
        assertEquals(sym[TEMPLATE], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(3, r.endOffset());
    }

    @Test
    void templateCharsEmptySubstitutionStart() {
        assertNull(run("${}", 0, TEMPLATE), "no content before ${ -> has_content false");
    }

    @Test
    void templateCharsDollarWithoutBraceConsumesIt() {
        ScannerVM.Result r = run("$`x", 0, TEMPLATE);
        assertEquals(sym[TEMPLATE], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(1, r.endOffset());
    }

    @Test
    void templateCharsUnterminatedFails() {
        assertNull(run("abc", 0, TEMPLATE), "EOF -> scan_template_chars returns false");
    }

    @Test
    void templateCharsBackslashFirstCharFails() {
        assertNull(run("\\`", 0, TEMPLATE), "backslash on the first iteration -> has_content false");
    }

    @Test
    void templateCharsBackslashAfterContentAccepts() {
        ScannerVM.Result r = run("a\\`", 0, TEMPLATE);
        assertEquals(sym[TEMPLATE], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(1, r.endOffset());
    }

    // ------------------------------------------------------------------
    // scan_jsx_text
    // ------------------------------------------------------------------

    @Test
    void jsxTextUpToTagChar() {
        ScannerVM.Result r = run("hello<", 0, JSX);
        assertEquals(sym[JSX], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(5, r.endOffset());
    }

    @Test
    void jsxTextLeadingWhitespaceIsText() {
        ScannerVM.Result r = run("  ", 0, JSX);
        assertEquals(sym[JSX], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(2, r.endOffset(), "whitespace not after a newline counts as text");
    }

    @Test
    void jsxTextWhitespaceAfterNewlineIsNotText() {
        assertNull(run("\n  ", 0, JSX), "whitespace directly after a newline is not text");
    }

    @Test
    void jsxTextNewlineThenContentIsText() {
        ScannerVM.Result r = run("\nX", 0, JSX);
        assertEquals(sym[JSX], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(2, r.endOffset());
    }

    @Test
    void jsxTextStopsAtAmpersand() {
        assertNull(run("&", 0, JSX), "& terminates jsx text without content");
    }

    // ------------------------------------------------------------------
    // scan_automatic_semicolon
    // ------------------------------------------------------------------

    @Test
    void asiRejectedBeforeIdentifier() {
        assertNull(run("abc", 0, ASI), "non-whitespace lookahead -> ASI false");
    }

    @Test
    void asiAfterNewlineIsZeroWidth() {
        ScannerVM.Result r = run("\n", 0, ASI);
        assertEquals(sym[ASI], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(0, r.endOffset(), "skipped bytes are padding, token is zero-width");
    }

    @Test
    void asiBeforeClosingBrace() {
        ScannerVM.Result r = run("\n}", 0, ASI);
        assertEquals(sym[ASI], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(0, r.endOffset());
    }

    @Test
    void asiBeforeDecimal() {
        assertEquals(sym[ASI], run("\n.5", 0, ASI).symbol());
        assertNull(run("\n.x", 0, ASI), ". followed by a non-digit -> no ASI");
    }

    @Test
    void asiBeforeIncDec() {
        assertEquals(sym[ASI], run("\n++", 0, ASI).symbol());
        assertEquals(sym[ASI], run("\n--", 0, ASI).symbol());
        assertNull(run("\n+=", 0, ASI), "binary + -> no ASI");
    }

    @Test
    void asiBeforeNotEquals() {
        assertEquals(sym[ASI], run("\n!a", 0, ASI).symbol(), "unary ! -> ASI");
        assertNull(run("\n!=", 0, ASI), "!= -> no ASI");
    }

    @Test
    void asiBeforeInAndInstanceof() {
        assertNull(run("\nin", 0, ASI), "the keyword 'in' -> no ASI");
        assertNull(run("\nin ", 0, ASI), "'in' followed by non-alpha -> no ASI");
        assertEquals(sym[ASI], run("\ninX", 0, ASI).symbol(), "identifier starting with in -> ASI");
        assertNull(run("\ninstanceof ", 0, ASI), "the keyword 'instanceof' -> no ASI");
        assertEquals(sym[ASI], run("\ninstanceofX", 0, ASI).symbol(),
                "identifier continuing after instanceof -> ASI");
    }

    @Test
    void asiAfterBlockCommentWithoutNewline() {
        // C: NO_NEWLINE falls through to the !iswspace(EOF)=!false check ->
        // reject (verified against the vendored scanner.c on macOS).
        assertNull(run("/*c*/", 0, ASI), "comment without newline at EOF -> no ASI");
    }

    @Test
    void asiAfterBlockCommentWithNewline() {
        ScannerVM.Result r = run("/*\n*/x", 0, ASI);
        assertEquals(sym[ASI], r.symbol(), "block comment with newline -> ACCEPT + comment_condition");
        assertEquals(0, r.startOffset());
        assertEquals(0, r.endOffset());
    }

    @Test
    void asiRejectedOnBareSlash() {
        assertNull(run("/", 0, ASI), "lone / -> REJECT");
    }

    @Test
    void asiAfterLineComment() {
        ScannerVM.Result r = run("//line", 0, ASI);
        assertEquals(sym[ASI], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(0, r.endOffset());
    }

    @Test
    void asiRejectedBeforeForbiddenChars() {
        assertNull(run("\n;", 0, ASI), "semicolon -> no ASI");
        assertNull(run("\n/", 0, ASI), "slash -> no ASI");
        assertNull(run("\n?", 0, ASI), "question mark -> no ASI");
    }

    // ------------------------------------------------------------------
    // scan_ternary_qmark
    // ------------------------------------------------------------------

    @Test
    void ternaryQmarkSimple() {
        ScannerVM.Result r = run("?a", 0, TERNARY);
        assertEquals(sym[TERNARY], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(1, r.endOffset());
    }

    @Test
    void ternaryQmarkSkipsLeadingWhitespace() {
        ScannerVM.Result r = run(" ?", 0, TERNARY);
        assertEquals(sym[TERNARY], r.symbol());
        assertEquals(1, r.startOffset(), "skipped space is padding, not token content");
        assertEquals(2, r.endOffset());
    }

    @Test
    void ternaryQmarkNullCoalescingRejected() {
        assertNull(run("??", 0, TERNARY));
    }

    @Test
    void ternaryQmarkOptionalChainingDigit() {
        // The mark_end happens right after the '?', so the token is just the
        // '?'; the consumed ".5" is re-lexed by the next token (C semantics).
        ScannerVM.Result r = run("?.5", 0, TERNARY);
        assertEquals(sym[TERNARY], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(1, r.endOffset());
        assertNull(run("?.", 0, TERNARY), "?. without a digit -> no token");
    }

    // ------------------------------------------------------------------
    // scan_html_comment
    // ------------------------------------------------------------------

    @Test
    void htmlCommentOpenForm() {
        ScannerVM.Result r = run("<!--hi", 0, HTML);
        assertEquals(sym[HTML], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(6, r.endOffset(), "content runs to EOF");
    }

    @Test
    void htmlCommentSkipsLeadingWhitespace() {
        ScannerVM.Result r = run("  <!--x", 0, HTML);
        assertEquals(sym[HTML], r.symbol());
        assertEquals(2, r.startOffset(), "leading whitespace is padding");
        assertEquals(7, r.endOffset());
    }

    @Test
    void htmlCommentCloseForm() {
        ScannerVM.Result r = run("-->x", 0, HTML);
        assertEquals(sym[HTML], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(4, r.endOffset(), "content runs to EOF past the closing -->");
    }

    @Test
    void htmlCommentMismatchedPrefixRejected() {
        assertNull(run("<!-x", 0, HTML));
        assertNull(run("x", 0, HTML));
    }

    // ------------------------------------------------------------------
    // dispatcher gates
    // ------------------------------------------------------------------

    @Test
    void templateCharsSuppressedWhenAsiAlsoValid() {
        assertNull(run("abc`", 0, TEMPLATE, ASI), "scanner.c rejects when both are valid");
    }

    @Test
    void htmlCommentSuppressedByOtherValidSymbols() {
        assertNull(run("<!--x", 0, LOGICAL_OR), "LOGICAL_OR valid -> html_comment suppressed");
        assertNull(run("<!--x", 0, ESCAPE));
        assertNull(run("<!--x", 0, REGEX));
    }

    @Test
    void dispatcherFallsThroughToDfaForGatedOnlySymbols() {
        assertNull(run("?x", 0, LOGICAL_OR), "only LOGICAL_OR valid -> nothing emitted");
        assertNull(run("?x", 0, REGEX));
        assertNull(run("abc", 0, ESCAPE));
    }

    @Test
    void asiFailureFallsBackToTernaryWhenLookaheadIsQuestion() {
        ScannerVM.Result r = run("?", 0, ASI, TERNARY);
        assertEquals(sym[TERNARY], r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(1, r.endOffset());
    }

    @Test
    void ternaryFallbackSuppressedWhenCommentWasScanned() {
        assertNull(run("/*x*/?", 0, ASI, TERNARY),
                "scanned_comment=true -> no ternary fallback");
    }

    @Test
    void asiFailureWithoutTernaryFallsThrough() {
        assertNull(run("x", 0, ASI));
        assertNull(run("x", 0, ASI, TERNARY), "reject without '?' lookahead -> no fallback");
    }
}