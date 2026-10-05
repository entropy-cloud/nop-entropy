package io.nop.treesitter.scanner;

import static io.nop.treesitter.scanner.BashCharClass.isAlpha;
import static io.nop.treesitter.scanner.BashCharClass.isAlnum;
import static io.nop.treesitter.scanner.BashCharClass.isSpace;

/**
 * The {@code extglob_pattern:} region of the upstream {@code scanner.c}.
 *
 * <p>Recognises extended-globbing patterns and, crucially, the case-arm
 * terminators ({@code )} / {@code |}) that the C scanner disambiguates from an
 * extglob closing paren by tracking {@code last_glob_paren_depth} across calls.
 * Reached by falling through the {@code regex:} region or by a
 * {@code goto extglob_pattern} from the {@code TEST_OPERATOR} and
 * variable-name regions.</p>
 *
 * <p>Unlike {@link BashRegexScanner#region} this region never falls through: when
 * its outer guard holds, every path returns.</p>
 */
final class BashExtglobScanner {

    static final int EXTGLOB_PATTERN = 14;

    private BashExtglobScanner() {
    }

    private static final class State {
        boolean done;
        boolean sawNonAlphaDot;
        int parenDepth;
        int bracketDepth;
        int braceDepth;
    }

    static int region(BashScannerState state, ExternalScanContext ctx, boolean[] valid) {
        if (!valid[EXTGLOB_PATTERN] || BashScanner.inErrorRecovery(valid)) {
            return BashScanFlow.CONTINUE;
        }

        // first skip ws, then check for ? * + @ !
        while (isSpace(ctx.lookahead())) {
            ctx.advance(true);
        }

        int c = ctx.lookahead();
        if (c != '?' && c != '*' && c != '+' && c != '@' && c != '!' && c != '-' && c != ')'
                && c != '\\' && c != '.' && c != '[' && !isAlpha(c)) {
            state.lastGlobParenDepth = 0;
            return BashScanFlow.NO_MATCH;
        }

        if (ctx.lookahead() == '\\') {
            ctx.advance(false);
            int next = ctx.lookahead();
            if ((isSpace(next) || next == '"') && next != '\r' && next != '\n') {
                ctx.advance(false);
            } else {
                return BashScanFlow.NO_MATCH;
            }
        }

        if (ctx.lookahead() == ')' && state.lastGlobParenDepth == 0) {
            ctx.markEnd();
            ctx.advance(false);
            if (isSpace(ctx.lookahead())) {
                return BashScanFlow.NO_MATCH;
            }
        }

        ctx.markEnd();
        boolean wasNonAlpha = !isAlpha(ctx.lookahead());
        if (ctx.lookahead() != '[') {
            // no esac
            if (ctx.lookahead() == 'e') {
                ctx.markEnd();
                ctx.advance(false);
                if (ctx.lookahead() == 's') {
                    ctx.advance(false);
                    if (ctx.lookahead() == 'a') {
                        ctx.advance(false);
                        if (ctx.lookahead() == 'c') {
                            ctx.advance(false);
                            if (isSpace(ctx.lookahead())) {
                                return BashScanFlow.NO_MATCH;
                            }
                        }
                    }
                }
            } else {
                ctx.advance(false);
            }
        }

        // -\w is just a word, find something else special
        if (ctx.lookahead() == '-') {
            ctx.markEnd();
            ctx.advance(false);
            while (isAlnum(ctx.lookahead())) {
                ctx.advance(false);
            }
            if (ctx.lookahead() == ')' || ctx.lookahead() == '\\' || ctx.lookahead() == '.') {
                return BashScanFlow.NO_MATCH;
            }
            ctx.markEnd();
        }

        // case item -) or *)
        if (ctx.lookahead() == ')' && state.lastGlobParenDepth == 0) {
            ctx.markEnd();
            ctx.advance(false);
            if (isSpace(ctx.lookahead())) {
                ctx.setResultSymbol(EXTGLOB_PATTERN);
                return wasNonAlpha ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
            }
        }

        if (isSpace(ctx.lookahead())) {
            ctx.markEnd();
            ctx.setResultSymbol(EXTGLOB_PATTERN);
            state.lastGlobParenDepth = 0;
            return BashScanFlow.MATCHED;
        }

        if (ctx.lookahead() == '$') {
            ctx.markEnd();
            ctx.advance(false);
            if (ctx.lookahead() == '{' || ctx.lookahead() == '(') {
                ctx.setResultSymbol(EXTGLOB_PATTERN);
                return BashScanFlow.MATCHED;
            }
        }

        if (ctx.lookahead() == '|') {
            ctx.markEnd();
            ctx.advance(false);
            ctx.setResultSymbol(EXTGLOB_PATTERN);
            return BashScanFlow.MATCHED;
        }

        c = ctx.lookahead();
        if (!isAlnum(c) && c != '(' && c != '"' && c != '[' && c != '?' && c != '/'
                && c != '\\' && c != '_' && c != '*') {
            return BashScanFlow.NO_MATCH;
        }

        State loop = new State();
        loop.sawNonAlphaDot = wasNonAlpha;
        loop.parenDepth = state.lastGlobParenDepth;
        while (!loop.done) {
            switch (ctx.lookahead()) {
                case 0:
                    return BashScanFlow.NO_MATCH;
                case '(':
                    loop.parenDepth++;
                    break;
                case '[':
                    loop.bracketDepth++;
                    break;
                case '{':
                    loop.braceDepth++;
                    break;
                case ')':
                    if (loop.parenDepth == 0) {
                        loop.done = true;
                    }
                    loop.parenDepth--;
                    break;
                case ']':
                    if (loop.bracketDepth == 0) {
                        loop.done = true;
                    }
                    loop.bracketDepth--;
                    break;
                case '}':
                    if (loop.braceDepth == 0) {
                        loop.done = true;
                    }
                    loop.braceDepth--;
                    break;
                default:
                    break;
            }

            if (ctx.lookahead() == '|') {
                ctx.markEnd();
                ctx.advance(false);
                if (loop.parenDepth == 0 && loop.bracketDepth == 0 && loop.braceDepth == 0) {
                    ctx.setResultSymbol(EXTGLOB_PATTERN);
                    return BashScanFlow.MATCHED;
                }
            }

            if (!loop.done) {
                boolean wasSpace = isSpace(ctx.lookahead());
                if (ctx.lookahead() == '$') {
                    ctx.markEnd();
                    if (!isAlpha(ctx.lookahead()) && ctx.lookahead() != '.' && ctx.lookahead() != '\\') {
                        loop.sawNonAlphaDot = true;
                    }
                    ctx.advance(false);
                    if (ctx.lookahead() == '(' || ctx.lookahead() == '{') {
                        ctx.setResultSymbol(EXTGLOB_PATTERN);
                        state.lastGlobParenDepth = loop.parenDepth;
                        return loop.sawNonAlphaDot ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
                    }
                }
                if (wasSpace) {
                    ctx.markEnd();
                    ctx.setResultSymbol(EXTGLOB_PATTERN);
                    state.lastGlobParenDepth = 0;
                    return loop.sawNonAlphaDot ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
                }
                if (ctx.lookahead() == '"') {
                    ctx.markEnd();
                    ctx.setResultSymbol(EXTGLOB_PATTERN);
                    state.lastGlobParenDepth = 0;
                    return loop.sawNonAlphaDot ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
                }
                if (ctx.lookahead() == '\\') {
                    if (!isAlpha(ctx.lookahead()) && ctx.lookahead() != '.' && ctx.lookahead() != '\\') {
                        loop.sawNonAlphaDot = true;
                    }
                    ctx.advance(false);
                    if (isSpace(ctx.lookahead()) || ctx.lookahead() == '"') {
                        ctx.advance(false);
                    }
                } else {
                    if (!isAlpha(ctx.lookahead()) && ctx.lookahead() != '.' && ctx.lookahead() != '\\') {
                        loop.sawNonAlphaDot = true;
                    }
                    ctx.advance(false);
                }
                if (!wasSpace) {
                    ctx.markEnd();
                }
            }
        }

        ctx.setResultSymbol(EXTGLOB_PATTERN);
        state.lastGlobParenDepth = 0;
        return loop.sawNonAlphaDot ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
    }
}
