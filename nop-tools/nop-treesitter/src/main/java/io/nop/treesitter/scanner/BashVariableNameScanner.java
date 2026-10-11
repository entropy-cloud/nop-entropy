package io.nop.treesitter.scanner;

import static io.nop.treesitter.scanner.BashCharClass.isAlpha;
import static io.nop.treesitter.scanner.BashCharClass.isDigit;
import static io.nop.treesitter.scanner.BashCharClass.isSpace;
import static io.nop.treesitter.scanner.BashExpansionScanner.EXPANSION_WORD;
import static io.nop.treesitter.scanner.BashExtglobScanner.EXTGLOB_PATTERN;
import static io.nop.treesitter.scanner.BashRegexScanner.REGEX_NO_SLASH;
import static io.nop.treesitter.scanner.BashScanFlow.CONTINUE;
import static io.nop.treesitter.scanner.BashScanFlow.MATCHED;
import static io.nop.treesitter.scanner.BashScanFlow.NO_MATCH;

/**
 * The {@code VARIABLE_NAME} / {@code FILE_DESCRIPTOR} / {@code HEREDOC_ARROW}
 * region of the upstream {@code scanner.c}.
 *
 * <p>This is the largest single region of the C function: it skips inter-token
 * whitespace, recognises {@code <} and {@code <<} file descriptors and the
 * {@code <<} / {@code <<-} heredoc openers (the only place a heredoc is pushed
 * onto the scanner's cross-call stack), then decides whether the identifier it
 * consumed is a variable name — and it hosts three of the four {@code goto}
 * sites into the trailing labels.</p>
 */
final class BashVariableNameScanner {

    // external token ordinals
    private static final int VARIABLE_NAME = 8;
    private static final int FILE_DESCRIPTOR = 5;
    private static final int HEREDOC_ARROW = 23;
    private static final int HEREDOC_ARROW_DASH = 24;
    private static final int NEWLINE = 25;
    private static final int OPENING_PAREN = 26;
    private static final int CLOSING_BRACE = 21;

    private BashVariableNameScanner() {
    }

    /**
     * The variable-name / file-descriptor / heredoc-arrow region of
     * {@code scanner.c}. It owns the {@code <<} and {@code <<-} recognition (the
     * only place a heredoc is pushed on the scanner's stack) and the three
     * {@code goto} sites that jump to the {@code expansion_word:},
     * {@code brace_start:} and {@code extglob_pattern:} labels. It reports those
     * as {@link BashScanFlow} JUMP_* sentinels, which {@link BashScanner} resolves
     * against the shared scanner state.
     */
    static int region(BashScannerState state, ExternalScanContext ctx, boolean[] valid) {
        if (!(valid[VARIABLE_NAME] || valid[FILE_DESCRIPTOR] || valid[HEREDOC_ARROW])
                || valid[REGEX_NO_SLASH] || BashScanner.inErrorRecovery(valid)) {
            return CONTINUE;
        }
        for (;;) {
            int c = ctx.lookahead();
            if ((c == ' ' || c == '\t' || c == '\r' || (c == '\n' && !valid[NEWLINE]))
                    && !valid[EXPANSION_WORD]) {
                ctx.advance(true);
            } else if (c == '\\') {
                ctx.advance(true);

                if (ctx.eof()) {
                    ctx.markEnd();
                    ctx.setResultSymbol(VARIABLE_NAME);
                    return MATCHED;
                }

                if (ctx.lookahead() == '\r') {
                    ctx.advance(true);
                }
                if (ctx.lookahead() == '\n') {
                    ctx.advance(true);
                } else {
                    if (ctx.lookahead() == '\\' && valid[EXPANSION_WORD]) {
                        return BashScanFlow.JUMP_EXPANSION_WORD;
                    }
                    return NO_MATCH;
                }
            } else {
                break;
            }
        }

        // no '*', '@', '?', '-', '$', '0', '_'
        if (!valid[EXPANSION_WORD]) {
            int c = ctx.lookahead();
            if (c == '*' || c == '@' || c == '?' || c == '-' || c == '0' || c == '_') {
                ctx.markEnd();
                ctx.advance(false);
                int next = ctx.lookahead();
                if (next == '=' || next == '[' || next == ':' || next == '-' || next == '%'
                        || next == '#' || next == '/') {
                    return NO_MATCH;
                }
                if (valid[EXTGLOB_PATTERN] && isSpace(ctx.lookahead())) {
                    ctx.markEnd();
                    ctx.setResultSymbol(EXTGLOB_PATTERN);
                    return MATCHED;
                }
            }
        }

        if (valid[HEREDOC_ARROW] && ctx.lookahead() == '<') {
            ctx.advance(false);
            if (ctx.lookahead() == '<') {
                ctx.advance(false);
                if (ctx.lookahead() == '-') {
                    ctx.advance(false);
                    state.pushHeredoc(true);
                    ctx.setResultSymbol(HEREDOC_ARROW_DASH);
                } else if (ctx.lookahead() == '<' || ctx.lookahead() == '=') {
                    return NO_MATCH;
                } else {
                    state.pushHeredoc(false);
                    ctx.setResultSymbol(HEREDOC_ARROW);
                }
                return MATCHED;
            }
            return NO_MATCH;
        }

        boolean isNumber = true;
        if (isDigit(ctx.lookahead())) {
            ctx.advance(false);
        } else if (isAlpha(ctx.lookahead()) || ctx.lookahead() == '_') {
            isNumber = false;
            ctx.advance(false);
        } else {
            if (ctx.lookahead() == '{') {
                return BashScanFlow.JUMP_BRACE_START;
            }
            if (valid[EXPANSION_WORD]) {
                return BashScanFlow.JUMP_EXPANSION_WORD;
            }
            if (valid[EXTGLOB_PATTERN]) {
                return BashScanFlow.JUMP_EXTGLOB;
            }
            return NO_MATCH;
        }

        for (;;) {
            if (isDigit(ctx.lookahead())) {
                ctx.advance(false);
            } else if (isAlpha(ctx.lookahead()) || ctx.lookahead() == '_') {
                isNumber = false;
                ctx.advance(false);
            } else {
                break;
            }
        }

        if (isNumber && valid[FILE_DESCRIPTOR] && (ctx.lookahead() == '>' || ctx.lookahead() == '<')) {
            ctx.setResultSymbol(FILE_DESCRIPTOR);
            return MATCHED;
        }

        if (valid[VARIABLE_NAME]) {
            if (ctx.lookahead() == '+') {
                ctx.markEnd();
                ctx.advance(false);
                if (ctx.lookahead() == '=' || ctx.lookahead() == ':' || valid[CLOSING_BRACE]) {
                    ctx.setResultSymbol(VARIABLE_NAME);
                    return MATCHED;
                }
                return NO_MATCH;
            }
            if (ctx.lookahead() == '/') {
                return NO_MATCH;
            }
            if (ctx.lookahead() == '=' || ctx.lookahead() == '['
                    || (ctx.lookahead() == ':' && !valid[CLOSING_BRACE] && !valid[OPENING_PAREN])
                    || ctx.lookahead() == '%'
                    || (ctx.lookahead() == '#' && !isNumber) || ctx.lookahead() == '@'
                    || (ctx.lookahead() == '-' && valid[CLOSING_BRACE])) {
                ctx.markEnd();
                ctx.setResultSymbol(VARIABLE_NAME);
                return MATCHED;
            }

            if (ctx.lookahead() == '?') {
                ctx.markEnd();
                ctx.advance(false);
                ctx.setResultSymbol(VARIABLE_NAME);
                return isAlpha(ctx.lookahead()) ? MATCHED : NO_MATCH;
            }
        }

        return NO_MATCH;
    }
}
