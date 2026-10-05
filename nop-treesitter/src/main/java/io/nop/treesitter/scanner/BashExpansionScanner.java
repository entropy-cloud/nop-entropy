package io.nop.treesitter.scanner;

import static io.nop.treesitter.scanner.BashCharClass.isAlnum;
import static io.nop.treesitter.scanner.BashCharClass.isDigit;
import static io.nop.treesitter.scanner.BashCharClass.isSpace;

/**
 * The {@code expansion_word:} and {@code brace_start:} regions of the upstream
 * {@code scanner.c} — the last two labels before the function's final
 * {@code return false}.
 *
 * <p>{@code expansion_word} recognises a "word" glued to an expansion
 * ({@code pre"$X"post}, {@code foo$(cmd)}). {@code brace_start} recognises the
 * numeric brace-expansion opener ({@code {1..10}}). Because they are adjacent
 * labels, an {@code expansion_word} guard miss falls straight through to
 * {@code brace_start}, and a {@code brace_start} guard miss falls straight to
 * the end of the function.</p>
 */
final class BashExpansionScanner {

    static final int EXPANSION_WORD = 13;
    static final int BRACE_START = 16;

    private BashExpansionScanner() {
    }

    static int regionExpansionWord(ExternalScanContext ctx, boolean[] valid) {
        if (!valid[EXPANSION_WORD]) {
            return BashScanFlow.CONTINUE;
        }
        boolean advancedOnce = false;
        boolean advanceOnceSpace = false;
        for (;;) {
            if (ctx.lookahead() == '"') {
                return BashScanFlow.NO_MATCH;
            }
            if (ctx.lookahead() == '$') {
                ctx.markEnd();
                ctx.advance(false);
                int next = ctx.lookahead();
                if (next == '{' || next == '(' || next == '\'' || isAlnum(next)) {
                    ctx.setResultSymbol(EXPANSION_WORD);
                    return advancedOnce ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
                }
                advancedOnce = true;
            }

            if (ctx.lookahead() == '}') {
                ctx.markEnd();
                ctx.setResultSymbol(EXPANSION_WORD);
                return (advancedOnce || advanceOnceSpace)
                        ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
            }

            if (ctx.lookahead() == '(' && !(advancedOnce || advanceOnceSpace)) {
                ctx.markEnd();
                ctx.advance(false);
                while (ctx.lookahead() != ')' && !ctx.eof()) {
                    // if we find a $( or ${ assume this is valid and is
                    // a garbage concatenation of some weird word + an
                    // expansion
                    if (ctx.lookahead() == '$') {
                        ctx.markEnd();
                        ctx.advance(false);
                        int next = ctx.lookahead();
                        if (next == '{' || next == '(' || next == '\'' || isAlnum(next)) {
                            ctx.setResultSymbol(EXPANSION_WORD);
                            return advancedOnce ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
                        }
                        advancedOnce = true;
                    } else {
                        advancedOnce = advancedOnce || !isSpace(ctx.lookahead());
                        advanceOnceSpace = advanceOnceSpace || isSpace(ctx.lookahead());
                        ctx.advance(false);
                    }
                }
                ctx.markEnd();
                if (ctx.lookahead() == ')') {
                    advancedOnce = true;
                    ctx.advance(false);
                    ctx.markEnd();
                    if (ctx.lookahead() == '}') {
                        return BashScanFlow.NO_MATCH;
                    }
                } else {
                    return BashScanFlow.NO_MATCH;
                }
            }

            if (ctx.lookahead() == '\'') {
                return BashScanFlow.NO_MATCH;
            }

            if (ctx.eof()) {
                return BashScanFlow.NO_MATCH;
            }
            advancedOnce = advancedOnce || !isSpace(ctx.lookahead());
            advanceOnceSpace = advanceOnceSpace || isSpace(ctx.lookahead());
            ctx.advance(false);
        }
    }

    static int regionBraceStart(ExternalScanContext ctx, boolean[] valid) {
        if (!valid[BRACE_START] || BashScanner.inErrorRecovery(valid)) {
            return BashScanFlow.NO_MATCH;
        }
        while (isSpace(ctx.lookahead())) {
            ctx.advance(true);
        }

        if (ctx.lookahead() != '{') {
            return BashScanFlow.NO_MATCH;
        }

        ctx.advance(false);
        ctx.markEnd();

        while (isDigit(ctx.lookahead())) {
            ctx.advance(false);
        }

        if (ctx.lookahead() != '.') {
            return BashScanFlow.NO_MATCH;
        }
        ctx.advance(false);

        if (ctx.lookahead() != '.') {
            return BashScanFlow.NO_MATCH;
        }
        ctx.advance(false);

        while (isDigit(ctx.lookahead())) {
            ctx.advance(false);
        }

        if (ctx.lookahead() != '}') {
            return BashScanFlow.NO_MATCH;
        }

        ctx.setResultSymbol(BRACE_START);
        return BashScanFlow.MATCHED;
    }
}
