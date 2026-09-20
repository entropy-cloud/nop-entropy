package io.nop.treesitter.lexer;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.scanner.ExternalScanContext;
import io.nop.treesitter.scanner.ExternalScanner;
import io.nop.treesitter.scanner.ScannerVM;

import java.util.List;

/**
 * Byte-stream lexer driven by the per-language lexer automata carried in the
 * grammar blob (decoded from the {@code ts_lex} / {@code ts_lex_keywords}
 * function bodies by {@code ParserCExtractor}).
 *
 * <p>Scanning follows the C runtime DFA semantics: at each state the accept is
 * recorded (at state entry for {@code acceptAtEntry} states, at the dead end
 * otherwise), then the ordered transitions are tried and the first match
 * consumes the lookahead codepoint (as token padding when the transition is a
 * C {@code SKIP}); when no transition matches the token ends at the last
 * accepted position. A byte (or EOF) that no transition can consume and no
 * state accepts raises {@link TreeSitterException} — nothing is skipped or
 * silently defaulted.</p>
 *
 * <p>Keyword capture mirrors the C runtime: when the main lexer produces the
 * grammar's {@code keyword_capture_token} (e.g. {@code sym_identifier} for
 * Java), the keyword lexer re-lexes the same span from state 0; if it accepts
 * the full span with a symbol the current parse state can shift, the token's
 * symbol is replaced by the keyword symbol.</p>
 */
public final class Lexer {

    /** Built-in end-of-input token (symbol id 0). */
    public static final int END_SYMBOL = 0;

    /** Token produced by a single lexer step. Offsets are byte offsets into the source. */
    public record Token(int symbol, int startOffset, int endOffset, boolean keyword) {

        public Token(int symbol, int startOffset, int endOffset) {
            this(symbol, startOffset, endOffset, false);
        }
    }

    /**
     * Either a regular token or the C runtime's builtin-error leaf (C
     * {@code ts_subtree_new_error}) spanning the bytes skipped after no lex
     * mode could match; {@code errorChar} is the first unrecognized character.
     */
    public record LexOutcome(Token token, boolean error, int errorStart, int errorEnd, int errorChar) {

        public static LexOutcome ofToken(Token token) {
            return new LexOutcome(token, false, 0, 0, 0);
        }

        public static LexOutcome ofError(int start, int end, int ch) {
            return new LexOutcome(null, true, start, end, ch);
        }

        public boolean isError() {
            return error;
        }
    }

    private Lexer() {
    }

    /**
     * Scans the next token for the parser, with the C runtime's error fallback
     * (C {@code ts_parser__lex}): first the parse state's own lex mode, then —
     * on failure — the ERROR_STATE's lex mode, then character-by-character
     * skipping that produces a builtin-error leaf covering the skipped bytes.
     * Never throws for unlexable input.
     *
     * <p>{@code ignoreEmptyExternalTokens} mirrors the C runtime's guard for
     * empty external-scanner tokens (error mode / no progress since the last
     * error); an empty external token also ignored when shifting it would not
     * change the parse state. Deviation from C: the scanner-state-change clause
     * of that guard is not tracked (stateless scanners in all shipped
     * grammars).</p>
     */
    public static LexOutcome nextForParse(Language language, byte[] source, int position,
                                          int parseState, boolean ignoreEmptyExternalTokens,
                                          ExternalScanner externalScanner) {
        boolean errorMode = false;
        int errorStart = -1;
        int errorEnd = -1;
        int errorChar = 0;
        int pos = position;
        for (;;) {
            int modeState = errorMode ? Language.ERROR_STATE : parseState;
            if (language.externalLexState(modeState) != 0) {
                ScannerVM.Result ext = externalScan(language, source, pos, modeState,
                        ignoreEmptyExternalTokens, externalScanner);
                if (ext != null && language.hasActions(modeState, ext.symbol())) {
                    return LexOutcome.ofToken(
                            new Token(ext.symbol(), ext.startOffset(), ext.endOffset(), false));
                }
            }
            int lexState = language.lexState(modeState);
            ScanOutcome r = scanFull(language.lexerAutomaton(), source, pos, lexState);
            if (r.accepted) {
                if (errorStart >= 0) {
                    return LexOutcome.ofError(errorStart, errorEnd, errorChar);
                }
                int symbol = r.symbol;
                boolean keyword = false;
                int capture = language.keywordCaptureToken();
                if (capture != 0 && symbol == capture && language.keywordLexerAutomaton() != null) {
                    ScanOutcome kw = scanFull(language.keywordLexerAutomaton(), source, r.tokenStart, 0);
                    if (kw.accepted && kw.tokenEnd == r.tokenEnd) {
                        keyword = true;
                        if (language.hasActions(modeState, kw.symbol)
                                || language.isReservedWord(modeState, kw.symbol)) {
                            symbol = kw.symbol;
                        }
                    }
                }
                return LexOutcome.ofToken(new Token(symbol, r.tokenStart, r.tokenEnd, keyword));
            }
            if (!errorMode) {
                errorMode = true;
                pos = position;
                continue;
            }
            if (errorStart < 0) {
                errorStart = pos;
                errorEnd = pos;
                int[] dec = decodeCodepoint(source, r.stopPosition);
                errorChar = dec == null ? 0 : dec[0];
            }
            int current = r.stopPosition;
            if (current == errorEnd) {
                if (current >= source.length) {
                    if (errorEnd == errorStart) {
                        return LexOutcome.ofToken(new Token(END_SYMBOL, current, current));
                    }
                    return LexOutcome.ofError(errorStart, errorEnd, errorChar);
                }
                int[] dec = decodeCodepoint(source, current);
                current += dec == null ? 1 : dec[1];
            }
            errorEnd = current;
            pos = current;
        }
    }

    /**
     * Scans the next token using an explicit lex state, without keyword capture
     * or parse-state selection. Intended for token-level tests and diagnostics.
     */
    public static Token lex(Language language, byte[] source, int position, int lexState) {
        ScanResult r = scan(language.lexerAutomaton(), source, position, lexState);
        if (r == null) {
            throw new TreeSitterException("lex error at byte offset " + position
                    + ": no valid token (lex state " + lexState + ")");
        }
        return new Token(r.symbol, r.tokenStart, r.tokenEnd);
    }

    /**
     * Runs the external scanner for the state: a registered Java scanner when
     * present, else the blob's bytecode program. Applies the C empty-token
     * guard in both paths.
     */
    private static ScannerVM.Result externalScan(Language language, byte[] source, int position,
                                                 int parseState, boolean ignoreEmptyExternalTokens,
                                                 ExternalScanner externalScanner) {
        if (externalScanner != null) {
            boolean[] valid = ScannerVM.validSymbols(language, parseState);
            JavaScanContext ctx = new JavaScanContext(source, position);
            if (externalScanner.scan(ctx, valid)) {
                int symbol = language.externalSymbolMap()[ctx.resultSymbol];
                boolean empty = ctx.markedEnd <= ctx.tokenStart;
                boolean tokenIsExtra = language.nextState(parseState, symbol) == parseState;
                if (empty && (ignoreEmptyExternalTokens || tokenIsExtra)) {
                    return null;
                }
                // No parse-table re-validation here (unlike the DSL path): C
                // consumes the scanner's token whenever the scanner accepted it
                // under the mode's valid-symbol list — required for python's
                // zero-width NEWLINE/INDENT/DEDENT tokens.
                return new ScannerVM.Result(symbol, ctx.tokenStart, ctx.markedEnd);
            }
            return null;
        }
        if (language.scannerProgram().length > 0) {
            return ScannerVM.scan(language, source, position, parseState);
        }
        return null;
    }

    /**
     * The per-call lexer view handed to Java external scanners: decodes the
     * current lookahead, advances by its UTF-8 width, tracks token start, the
     * marked end and the byte column.
     */
    private static final class JavaScanContext implements ExternalScanContext {
        private final byte[] source;
        private int pos;
        private int tokenStart;
        private int markedEnd;
        private int lookahead;
        private boolean eof;
        private int resultSymbol = -1;

        JavaScanContext(byte[] source, int position) {
            this.source = source;
            this.pos = position;
            this.tokenStart = position;
            this.markedEnd = position;
            reload();
        }

        private void reload() {
            int[] dec = decodeCodepoint(source, pos);
            eof = dec == null;
            lookahead = eof ? 0 : dec[0];
        }

        @Override
        public int lookahead() {
            return lookahead;
        }

        @Override
        public boolean eof() {
            return eof;
        }

        @Override
        public void advance(boolean skip) {
            if (!eof) {
                pos += decodeCodepoint(source, pos)[1];
            }
            if (skip) {
                tokenStart = pos;
            }
            reload();
        }

        @Override
        public void markEnd() {
            markedEnd = pos;
        }

        @Override
        public int getColumn() {
            int lineStart = pos;
            while (lineStart > 0 && source[lineStart - 1] != '\n') {
                lineStart--;
            }
            return pos - lineStart;
        }

        @Override
        public void setResultSymbol(int ordinal) {
            resultSymbol = ordinal;
        }
    }

    private record ScanResult(int symbol, int tokenStart, int tokenEnd) {
    }

    private record ScanOutcome(boolean accepted, int symbol, int tokenStart, int tokenEnd, int stopPosition) {
    }

    private static ScanResult scan(Language.LexerAutomaton dfa, byte[] source, int position, int entryState) {
        ScanOutcome r = scanFull(dfa, source, position, entryState);
        if (!r.accepted) {
            return null;
        }
        return new ScanResult(r.symbol, r.tokenStart, r.tokenEnd);
    }

    /**
     * Runs the DFA from {@code position}; on failure {@code accepted} is false
     * and {@code stopPosition} is the byte offset the automaton died at (the C
     * lexer's current position after a failed {@code ts_lex}), which drives the
     * error-skip loop's one-character-at-a-time advance.
     */
    private static ScanOutcome scanFull(Language.LexerAutomaton dfa, byte[] source, int position,
                                        int entryState) {
        int p = position;
        int tokenStart = position;
        int tokenEnd = position;
        int acceptSymbol = -1;
        int state = entryState;
        int maxSteps = source.length * 4 + 128;
        for (int steps = 0; steps < maxSteps; steps++) {
            if (dfa.acceptAtEntry[state] && dfa.acceptSymbol[state] >= 0) {
                acceptSymbol = dfa.acceptSymbol[state];
                tokenEnd = p;
            }
            long packed = decodePacked(source, p);
            boolean eof = (packed & 0xFF) == 0;
            int lookahead = (int) (packed >>> 8);
            Language.LexerAutomaton.Transition t = findTransition(dfa, state, lookahead, eof);
            if (t == null) {
                if (!dfa.acceptAtEntry[state] && dfa.acceptSymbol[state] >= 0) {
                    acceptSymbol = dfa.acceptSymbol[state];
                    tokenEnd = p;
                }
                break;
            }
            if (!eof) {
                p += (int) (packed & 0xFF);
            }
            state = t.targetState();
            if (t.skip()) {
                tokenStart = p;
            }
        }
        return new ScanOutcome(acceptSymbol >= 0, acceptSymbol, tokenStart, tokenEnd, p);
    }

    private static Language.LexerAutomaton.Transition findTransition(Language.LexerAutomaton dfa, int state,
                                                                     int lookahead, boolean eof) {
        for (Language.LexerAutomaton.Transition t : dfa.transitions[state]) {
            for (Language.LexerAutomaton.Clause clause : t.clauses()) {
                boolean matches = true;
                for (Language.LexerAutomaton.Literal lit : clause.literals()) {
                    if (!literalMatches(dfa, lit, lookahead, eof)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return t;
                }
            }
        }
        return null;
    }

    private static boolean literalMatches(Language.LexerAutomaton dfa, Language.LexerAutomaton.Literal lit,
                                          int lookahead, boolean eof) {
        switch (lit.kind()) {
            case Language.LexerAutomaton.Literal.CHAR_EQ -> {
                return lookahead == lit.a();
            }
            case Language.LexerAutomaton.Literal.CHAR_NEQ -> {
                return lookahead != lit.a();
            }
            case Language.LexerAutomaton.Literal.RANGE -> {
                return lookahead >= lit.a() && lookahead <= lit.b();
            }
            case Language.LexerAutomaton.Literal.SET -> {
                return setContains(dfa.charSets[lit.a()], lookahead);
            }
            case Language.LexerAutomaton.Literal.NONZERO -> {
                return lookahead != 0;
            }
            case Language.LexerAutomaton.Literal.EOF -> {
                return eof;
            }
            case Language.LexerAutomaton.Literal.NOT_EOF -> {
                return !eof;
            }
            default -> throw new TreeSitterException("unknown lexer literal kind: " + lit.kind());
        }
    }

    private static boolean setContains(List<int[]> ranges, int lookahead) {
        int lo = 0;
        int hi = ranges.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int[] range = ranges.get(mid);
            if (lookahead < range[0]) {
                hi = mid - 1;
            } else if (lookahead > range[1]) {
                lo = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Allocation-free decode for the hot scan loop: packs
     * {@code (codepoint << 8) | width}, with width 0 marking end of input.
     */
    private static long decodePacked(byte[] source, int p) {
        int len = source.length;
        if (p >= len) {
            return 0;
        }
        int b0 = source[p] & 0xFF;
        if (b0 < 0x80) {
            return (b0 << 8) | 1;
        }
        if ((b0 & 0xE0) == 0xC0 && p + 1 < len) {
            int b1 = source[p + 1] & 0xFF;
            if ((b1 & 0xC0) == 0x80) {
                return (((b0 & 0x1F) << 6) | (b1 & 0x3F)) << 8 | 2;
            }
            return (b0 << 8) | 1;
        }
        if ((b0 & 0xF0) == 0xE0 && p + 2 < len) {
            int b1 = source[p + 1] & 0xFF;
            int b2 = source[p + 2] & 0xFF;
            if ((b1 & 0xC0) == 0x80 && (b2 & 0xC0) == 0x80) {
                return (((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F)) << 8 | 3;
            }
            return (b0 << 8) | 1;
        }
        if ((b0 & 0xF8) == 0xF0 && p + 3 < len) {
            int b1 = source[p + 1] & 0xFF;
            int b2 = source[p + 2] & 0xFF;
            int b3 = source[p + 3] & 0xFF;
            if ((b1 & 0xC0) == 0x80 && (b2 & 0xC0) == 0x80 && (b3 & 0xC0) == 0x80) {
                return (((b0 & 0x07) << 18) | ((b1 & 0x3F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F)) << 8 | 4;
            }
            return (b0 << 8) | 1;
        }
        return (b0 << 8) | 1;
    }

    /**
     * Decodes the UTF-8 codepoint at {@code p}; returns {@code null} past the end
     * of input, else {@code {codepoint, width}}. Invalid sequences degrade to a
     * single byte value (like the C runtime's {@code TS_DECODE_ERROR} handling).
     */
    private static int[] decodeCodepoint(byte[] source, int p) {
        int len = source.length;
        if (p >= len) {
            return null;
        }
        int b0 = source[p] & 0xFF;
        if (b0 < 0x80) {
            return new int[]{b0, 1};
        }
        if ((b0 & 0xE0) == 0xC0 && p + 1 < len) {
            int b1 = source[p + 1] & 0xFF;
            if ((b1 & 0xC0) == 0x80) {
                return new int[]{((b0 & 0x1F) << 6) | (b1 & 0x3F), 2};
            }
            return new int[]{b0, 1};
        }
        if ((b0 & 0xF0) == 0xE0 && p + 2 < len) {
            int b1 = source[p + 1] & 0xFF;
            int b2 = source[p + 2] & 0xFF;
            if ((b1 & 0xC0) == 0x80 && (b2 & 0xC0) == 0x80) {
                return new int[]{((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F), 3};
            }
            return new int[]{b0, 1};
        }
        if ((b0 & 0xF8) == 0xF0 && p + 3 < len) {
            int b1 = source[p + 1] & 0xFF;
            int b2 = source[p + 2] & 0xFF;
            int b3 = source[p + 3] & 0xFF;
            if ((b1 & 0xC0) == 0x80 && (b2 & 0xC0) == 0x80 && (b3 & 0xC0) == 0x80) {
                return new int[]{((b0 & 0x07) << 18) | ((b1 & 0x3F) << 12)
                        | ((b2 & 0x3F) << 6) | (b3 & 0x3F), 4};
            }
            return new int[]{b0, 1};
        }
        return new int[]{b0, 1};
    }
}
