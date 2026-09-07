package io.nop.treesitter.lexer;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;

/**
 * Byte-stream lexer for the tree-sitter JSON grammar (blob grammar
 * {@code /grammars/json/tree-sitter-json-blob.bin}).
 *
 * <p>The blob carries per-parse-state <em>lex mode</em> ids but not the lexer
 * automaton itself (upstream keeps that inside the generated {@code ts_lex}
 * function), so the per-lex-state scanners are implemented here, translated
 * from the vendored {@code parser.c} (test resource
 * {@code upstream/grammars/tree-sitter-json/src/parser.c}). JSON has two lex
 * states: 0 (regular tokens, whitespace and comments skipped) and 1 (string
 * content, where whitespace is part of the token). The grammar has no keyword
 * lexer, so {@code keywordLexModeCount = 0} in the shipped blob.</p>
 *
 * <p>Scanning follows the upstream DFA semantics: bytes are consumed one at a
 * time; every accept marks the current position as a token end; when no further
 * transition applies the token ends at the last accept. An unexpected byte that
 * no state can consume raises {@link TreeSitterException} — nothing is skipped
 * or silently defaulted.</p>
 */
public final class Lexer {

    /** Built-in end-of-input token (symbol id 0). */
    public static final int END_SYMBOL = 0;

    /** Token produced by a single lexer step. Offsets are byte offsets into the source. */
    public record Token(int symbol, int startOffset, int endOffset) {
    }

    private Lexer() {
    }

    /**
     * Scans the next token from {@code source} starting at {@code position},
     * using the given lex state. Returns the end token (symbol 0) at end of
     * input; raises {@link TreeSitterException} for a byte that no transition
     * can consume.
     */
    public static Token next(Language language, byte[] source, int position, int lexState) {
        if (lexState == 0) {
            return scanRegular(language, source, position);
        }
        if (lexState == 1) {
            return scanStringContent(language, source, position);
        }
        throw new TreeSitterException("lex state " + lexState + " not implemented for this grammar");
    }

    private static Token scanRegular(Language language, byte[] source, int position) {
        int p = position;
        int len = source.length;
        while (p < len) {
            int c = source[p] & 0xFF;
            if (c == ' ' || (c >= '\t' && c <= '\r')) {
                p++;
            } else {
                break;
            }
        }
        if (p >= len) {
            return new Token(END_SYMBOL, p, p);
        }
        return scanDfa(language, source, p, Dfa.STATE_REGULAR);
    }

    private static Token scanStringContent(Language language, byte[] source, int position) {
        return scanDfa(language, source, position, Dfa.STATE_STRING);
    }

    /**
     * Drives the translated DFA. States and transitions mirror the vendored
     * {@code ts_lex} function; accept ids are the JSON grammar's symbol ids.
     */
    private static Token scanDfa(Language language, byte[] source, int start, int entryState) {
        int len = source.length;
        int p = start;
        int state = entryState;
        int acceptSymbol = -1;
        int acceptEnd = start;
        for (;;) {
            int accept = Dfa.acceptSymbol(state);
            if (accept >= 0) {
                acceptSymbol = accept;
                acceptEnd = p;
            }
            int c = p < len ? source[p] & 0xFF : 0;
            boolean eof = p >= len;
            int next = Dfa.transition(state, c, eof);
            if (next == Dfa.NO_TRANSITION) {
                break;
            }
            p++;
            state = next;
        }
        if (acceptSymbol < 0) {
            int c = p < len ? source[p] & 0xFF : 0;
            throw new TreeSitterException("lex error at byte offset " + start
                    + ": no transition for byte 0x" + Integer.toHexString(c)
                    + " (lex state " + (entryState == Dfa.STATE_STRING ? 1 : 0) + ")");
        }
        return new Token(acceptSymbol, start, acceptEnd);
    }

    /**
     * The JSON lexer DFA, translated from the {@code ts_lex} function of the
     * vendored grammar (states 0/20 for regular tokens, state 1 for string
     * content). Values are the C function's internal state numbers so the
     * translation can be diffed against the upstream source.
     */
    private static final class Dfa {

        static final int NO_TRANSITION = -1;

        // entry states
        static final int STATE_REGULAR = 0;
        static final int STATE_STRING = 1;

        // JSON grammar symbol ids produced by accepts (see ts_symbol_identifiers)
        private static final int SYM_END = 0;
        private static final int SYM_LBRACE = 1;
        private static final int SYM_COMMA = 2;
        private static final int SYM_RBRACE = 3;
        private static final int SYM_COLON = 4;
        private static final int SYM_LBRACK = 5;
        private static final int SYM_RBRACK = 6;
        private static final int SYM_DQUOTE = 7;
        private static final int SYM_STRING_CONTENT = 8;
        private static final int SYM_ESCAPE_SEQUENCE = 9;
        private static final int SYM_NUMBER = 10;
        private static final int SYM_TRUE = 11;
        private static final int SYM_FALSE = 12;
        private static final int SYM_NULL = 13;
        private static final int SYM_COMMENT = 14;

        private Dfa() {
        }

        static int acceptSymbol(int state) {
            return switch (state) {
                case 21 -> SYM_END;
                case 22 -> SYM_LBRACE;
                case 23 -> SYM_COMMA;
                case 24 -> SYM_RBRACE;
                case 25 -> SYM_COLON;
                case 26 -> SYM_LBRACK;
                case 27 -> SYM_RBRACK;
                case 28 -> SYM_DQUOTE;
                case 29, 30, 31, 32, 33 -> SYM_STRING_CONTENT;
                case 34 -> SYM_ESCAPE_SEQUENCE;
                case 35, 36, 37, 38 -> SYM_NUMBER;
                case 39 -> SYM_TRUE;
                case 40 -> SYM_FALSE;
                case 41 -> SYM_NULL;
                case 42, 43 -> SYM_COMMENT;
                default -> -1;
            };
        }

        static int transition(int state, int c, boolean eof) {
            return switch (state) {
                case 0 -> regularEntry(c, eof);
                case 20 -> regularEntry(c, eof);
                case 1 -> stringEntry(c);
                case 2 -> switch (c) {
                    case '"' -> 28;
                    case '/' -> 3;
                    default -> isWhiteSpace(c) ? 2 : NO_TRANSITION;
                };
                case 3 -> switch (c) {
                    case '*' -> 5;
                    case '/' -> 43;
                    default -> NO_TRANSITION;
                };
                case 4 -> switch (c) {
                    case '*' -> 4;
                    case '/' -> 42;
                    default -> c != 0 ? 5 : NO_TRANSITION;
                };
                case 5 -> switch (c) {
                    case '*' -> 4;
                    default -> c != 0 ? 5 : NO_TRANSITION;
                };
                case 6 -> isDigit1To9(c) ? 36 : c == '0' ? 35 : NO_TRANSITION;
                case 7 -> c == 'a' ? 10 : NO_TRANSITION;
                case 8 -> c == 'e' ? 39 : NO_TRANSITION;
                case 9 -> c == 'e' ? 40 : NO_TRANSITION;
                case 10 -> c == 'l' ? 14 : NO_TRANSITION;
                case 11 -> c == 'l' ? 41 : NO_TRANSITION;
                case 12 -> c == 'l' ? 11 : NO_TRANSITION;
                case 13 -> c == 'r' ? 15 : NO_TRANSITION;
                case 14 -> c == 's' ? 9 : NO_TRANSITION;
                case 15 -> c == 'u' ? 8 : NO_TRANSITION;
                case 16 -> c == 'u' ? 12 : NO_TRANSITION;
                case 17 -> c == '+' || c == '-' ? 19 : isDigit(c) ? 38 : NO_TRANSITION;
                case 18 -> switch (c) {
                    case '"', '/', '\\', 'b', 'f', 'n', 'r', 't', 'u' -> 34;
                    default -> NO_TRANSITION;
                };
                case 19 -> isDigit(c) ? 38 : NO_TRANSITION;
                case 29 -> stringContentTransition(c);
                case 30 -> switch (c) {
                    case '*' -> 30;
                    case '/' -> 33;
                    default -> isStringContentChar(c) ? 31 : NO_TRANSITION;
                };
                case 31 -> switch (c) {
                    case '*' -> 30;
                    default -> isStringContentChar(c) ? 31 : NO_TRANSITION;
                };
                case 32 -> switch (c) {
                    case '/' -> 29;
                    default -> isWhiteSpace(c) ? 32 : isStringContentChar(c) ? 33 : NO_TRANSITION;
                };
                case 33 -> isStringContentChar(c) ? 33 : NO_TRANSITION;
                case 35 -> numberAfterZero(c);
                case 36 -> switch (c) {
                    case '.' -> 37;
                    case 'e', 'E' -> 17;
                    default -> isDigit(c) ? 36 : NO_TRANSITION;
                };
                case 37 -> switch (c) {
                    case 'e', 'E' -> 17;
                    default -> isDigit(c) ? 37 : NO_TRANSITION;
                };
                case 38 -> isDigit(c) ? 38 : NO_TRANSITION;
                case 43 -> c != 0 && c != '\n' ? 43 : NO_TRANSITION;
                default -> NO_TRANSITION;
            };
        }

        private static int regularEntry(int c, boolean eof) {
            if (eof) {
                return 21;
            }
            return switch (c) {
                case '"' -> 28;
                case ',' -> 23;
                case '-' -> 6;
                case '/' -> 3;
                case '0' -> 35;
                case ':' -> 25;
                case '[' -> 26;
                case '\\' -> 18;
                case ']' -> 27;
                case 'f' -> 7;
                case 'n' -> 16;
                case 't' -> 13;
                case '{' -> 22;
                case '}' -> 24;
                default -> isWhiteSpace(c) ? 20 : isDigit1To9(c) ? 36 : NO_TRANSITION;
            };
        }

        private static int stringEntry(int c) {
            return switch (c) {
                case '\n' -> 2;
                case '"' -> 28;
                case '/' -> 29;
                case '\\' -> 18;
                default -> isWhiteSpace(c) ? 32 : c != 0 ? 33 : NO_TRANSITION;
            };
        }

        private static int stringContentTransition(int c) {
            return switch (c) {
                case '*' -> 31;
                case '/' -> 33;
                default -> isStringContentChar(c) ? 33 : NO_TRANSITION;
            };
        }

        private static int numberAfterZero(int c) {
            return switch (c) {
                case '.' -> 37;
                case 'e', 'E' -> 17;
                default -> NO_TRANSITION;
            };
        }

        private static boolean isWhiteSpace(int c) {
            return c == ' ' || (c >= '\t' && c <= '\r');
        }

        private static boolean isDigit(int c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isDigit1To9(int c) {
            return c >= '1' && c <= '9';
        }

        private static boolean isStringContentChar(int c) {
            return c != 0 && c != '\n' && c != '"' && c != '\\';
        }
    }
}