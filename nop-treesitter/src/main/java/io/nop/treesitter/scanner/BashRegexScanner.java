package io.nop.treesitter.scanner;

import static io.nop.treesitter.scanner.BashCharClass.isAlnum;
import static io.nop.treesitter.scanner.BashCharClass.isSpace;

/**
 * The {@code regex:} region of the upstream {@code scanner.c} — the three
 * regex flavors {@code REGEX} / {@code REGEX_NO_SLASH} / {@code REGEX_NO_SPACE}
 * and the shared depth-tracking loop that finds where the pattern ends.
 *
 * <p>Reached either by falling through the {@code BARE_DOLLAR} fallback region
 * or by a {@code goto regex} from the {@code TEST_OPERATOR} region.</p>
 */
final class BashRegexScanner {

    static final int REGEX = 10;
    static final int REGEX_NO_SLASH = 11;
    static final int REGEX_NO_SPACE = 12;

    private BashRegexScanner() {
    }

    private static final class State {
        boolean done;
        boolean advancedOnce;
        boolean foundNonAlnumDollarUnderDash;
        boolean lastWasEscape;
        boolean inSingleQuote;
        int parenDepth;
        int bracketDepth;
        int braceDepth;
    }

    static int region(ExternalScanContext ctx, boolean[] valid) {
        if (!(valid[REGEX] || valid[REGEX_NO_SLASH] || valid[REGEX_NO_SPACE])
                || BashScanner.inErrorRecovery(valid)) {
            return BashScanFlow.CONTINUE;
        }

        if (valid[REGEX] || valid[REGEX_NO_SPACE]) {
            while (isSpace(ctx.lookahead())) {
                ctx.advance(true);
            }
        }

        boolean noQuoteGuard = ctx.lookahead() != '"' && ctx.lookahead() != '\'';
        boolean quoteGuard = (ctx.lookahead() == '$' || ctx.lookahead() == '\'') && valid[REGEX_NO_SLASH];
        boolean spaceGuard = ctx.lookahead() == '\'' && valid[REGEX_NO_SPACE];
        if (!(noQuoteGuard || quoteGuard || spaceGuard)) {
            return BashScanFlow.CONTINUE;
        }

        if (ctx.lookahead() == '$' && valid[REGEX_NO_SLASH]) {
            ctx.markEnd();
            ctx.advance(false);
            if (ctx.lookahead() == '(') {
                return BashScanFlow.NO_MATCH;
            }
        }

        ctx.markEnd();

        State state = new State();
        while (!state.done) {
            if (state.inSingleQuote) {
                if (ctx.lookahead() == '\'') {
                    state.inSingleQuote = false;
                    ctx.advance(false);
                    ctx.markEnd();
                }
            }
            switch (ctx.lookahead()) {
                case '\\':
                    state.lastWasEscape = true;
                    break;
                case 0:
                    return BashScanFlow.NO_MATCH;
                case '(':
                    state.parenDepth++;
                    state.lastWasEscape = false;
                    break;
                case '[':
                    state.bracketDepth++;
                    state.lastWasEscape = false;
                    break;
                case '{':
                    if (!state.lastWasEscape) {
                        state.braceDepth++;
                    }
                    state.lastWasEscape = false;
                    break;
                case ')':
                    if (state.parenDepth == 0) {
                        state.done = true;
                    }
                    state.parenDepth--;
                    state.lastWasEscape = false;
                    break;
                case ']':
                    if (state.bracketDepth == 0) {
                        state.done = true;
                    }
                    state.bracketDepth--;
                    state.lastWasEscape = false;
                    break;
                case '}':
                    if (state.braceDepth == 0) {
                        state.done = true;
                    }
                    state.braceDepth--;
                    state.lastWasEscape = false;
                    break;
                case '\'':
                    // Enter or exit a single-quoted string.
                    state.inSingleQuote = !state.inSingleQuote;
                    ctx.advance(false);
                    state.advancedOnce = true;
                    state.lastWasEscape = false;
                    continue;
                default:
                    state.lastWasEscape = false;
                    break;
            }

            if (!state.done) {
                if (valid[REGEX]) {
                    boolean wasSpace = !state.inSingleQuote && isSpace(ctx.lookahead());
                    ctx.advance(false);
                    state.advancedOnce = true;
                    if (!wasSpace || state.parenDepth > 0) {
                        ctx.markEnd();
                    }
                } else if (valid[REGEX_NO_SLASH]) {
                    if (ctx.lookahead() == '/') {
                        ctx.markEnd();
                        ctx.setResultSymbol(REGEX_NO_SLASH);
                        return state.advancedOnce ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
                    }
                    if (ctx.lookahead() == '\\') {
                        ctx.advance(false);
                        state.advancedOnce = true;
                        if (!ctx.eof() && ctx.lookahead() != '[' && ctx.lookahead() != '/') {
                            ctx.advance(false);
                            ctx.markEnd();
                        }
                    } else {
                        boolean wasSpace = !state.inSingleQuote && isSpace(ctx.lookahead());
                        ctx.advance(false);
                        state.advancedOnce = true;
                        if (!wasSpace) {
                            ctx.markEnd();
                        }
                    }
                } else {
                    if (ctx.lookahead() == '\\') {
                        state.foundNonAlnumDollarUnderDash = true;
                        ctx.advance(false);
                        if (!ctx.eof()) {
                            ctx.advance(false);
                        }
                    } else if (ctx.lookahead() == '$') {
                        ctx.markEnd();
                        ctx.advance(false);
                        // do not parse a command substitution
                        if (ctx.lookahead() == '(') {
                            return BashScanFlow.NO_MATCH;
                        }
                        // end $ always means regex, e.g. 99999999$
                        if (isSpace(ctx.lookahead())) {
                            ctx.setResultSymbol(REGEX_NO_SPACE);
                            ctx.markEnd();
                            return BashScanFlow.MATCHED;
                        }
                    } else {
                        boolean wasSpace = !state.inSingleQuote && isSpace(ctx.lookahead());
                        if (wasSpace && state.parenDepth == 0) {
                            ctx.markEnd();
                            ctx.setResultSymbol(REGEX_NO_SPACE);
                            return state.foundNonAlnumDollarUnderDash
                                    ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
                        }
                        if (!isAlnum(ctx.lookahead()) && ctx.lookahead() != '$'
                                && ctx.lookahead() != '-' && ctx.lookahead() != '_') {
                            state.foundNonAlnumDollarUnderDash = true;
                        }
                        ctx.advance(false);
                    }
                }
            }
        }

        ctx.setResultSymbol(valid[REGEX_NO_SLASH] ? REGEX_NO_SLASH
                : valid[REGEX_NO_SPACE] ? REGEX_NO_SPACE : REGEX);
        if (valid[REGEX] && !state.advancedOnce) {
            return BashScanFlow.NO_MATCH;
        }
        return BashScanFlow.MATCHED;
    }
}
