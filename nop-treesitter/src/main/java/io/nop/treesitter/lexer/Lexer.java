package io.nop.treesitter.lexer;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;

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

    private Lexer() {
    }

    /**
     * Scans the next token from {@code source} starting at {@code position}
     * for the given parse state: the parse state selects the lex state, and
     * keyword capture applies when the grammar has a keyword lexer. Returns the
     * end token (symbol 0) at end of input; raises {@link TreeSitterException}
     * for input no transition can consume.
     */
    public static Token next(Language language, byte[] source, int position, int parseState) {
        int lexState = language.lexState(parseState);
        ScanResult r = scan(language.lexerAutomaton(), source, position, lexState);
        if (r == null) {
            throw new TreeSitterException("lex error at byte offset " + position
                    + ": no valid token (lex state " + lexState + ")");
        }
        int symbol = r.symbol;
        boolean keyword = false;
        int capture = language.keywordCaptureToken();
        if (capture != 0 && symbol == capture && language.keywordLexerAutomaton() != null) {
            ScanResult kw = scan(language.keywordLexerAutomaton(), source, r.tokenStart, 0);
            if (kw != null && kw.tokenEnd == r.tokenEnd) {
                keyword = true;
                if (language.hasActions(parseState, kw.symbol)) {
                    symbol = kw.symbol;
                }
            }
        }
        return new Token(symbol, r.tokenStart, r.tokenEnd, keyword);
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

    private record ScanResult(int symbol, int tokenStart, int tokenEnd) {
    }

    private static ScanResult scan(Language.LexerAutomaton dfa, byte[] source, int position, int entryState) {
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
            int[] dec = decodeCodepoint(source, p);
            boolean eof = dec == null;
            int lookahead = dec == null ? 0 : dec[0];
            Language.LexerAutomaton.Transition t = findTransition(dfa, state, lookahead, eof);
            if (t == null) {
                if (!dfa.acceptAtEntry[state] && dfa.acceptSymbol[state] >= 0) {
                    acceptSymbol = dfa.acceptSymbol[state];
                    tokenEnd = p;
                }
                break;
            }
            if (!eof) {
                p += dec[1];
            }
            state = t.targetState();
            if (t.skip()) {
                tokenStart = p;
            }
        }
        if (acceptSymbol < 0) {
            return null;
        }
        return new ScanResult(acceptSymbol, tokenStart, tokenEnd);
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