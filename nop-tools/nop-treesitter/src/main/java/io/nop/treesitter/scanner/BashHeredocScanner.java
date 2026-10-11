package io.nop.treesitter.scanner;

import io.nop.treesitter.scanner.BashCharClass.CString;
import io.nop.treesitter.scanner.BashScannerState.Heredoc;

import static io.nop.treesitter.scanner.BashCharClass.isSpace;
import static io.nop.treesitter.scanner.BashCharClass.isAlpha;

/**
 * The heredoc half of the upstream {@code scanner.c}: {@code advance_word},
 * {@code scan_heredoc_start}, {@code scan_heredoc_end_identifier} and
 * {@code scan_heredoc_content}, plus the two {@code scan()} regions that call
 * them ({@code HEREDOC_BODY_BEGINNING} / {@code SIMPLE_HEREDOC_BODY},
 * {@code HEREDOC_END}, {@code HEREDOC_CONTENT}, {@code HEREDOC_START}).
 *
 * <p>Token ordinals are the {@code scanner.c} {@code enum TokenType} order and
 * must stay aligned with {@code enum ts_external_scanner_symbol_identifiers} in
 * the vendored {@code src/parser.c} — see
 * {@code ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md}.</p>
 */
final class BashHeredocScanner {

    static final int HEREDOC_START = 0;
    static final int SIMPLE_HEREDOC_BODY = 1;
    static final int HEREDOC_BODY_BEGINNING = 2;
    static final int HEREDOC_CONTENT = 3;
    static final int HEREDOC_END = 4;

    private BashHeredocScanner() {
    }

    /**
     * {@code advance_word}: consumes a "word" in POSIX parlance and appends it
     * unquoted, terminating with a NUL. Returns {@code false} for an empty word
     * (or for a trailing lone backslash).
     *
     * <p>The delimiter buffer keeps the trailing NUL, so {@code delimiter.size}
     * counts it — the same accounting the C {@code serialize} relies on.</p>
     */
    static boolean advanceWord(ExternalScanContext ctx, CString unquotedWord) {
        boolean empty = true;

        int quote = 0;
        if (ctx.lookahead() == '\'' || ctx.lookahead() == '"') {
            quote = ctx.lookahead();
            ctx.advance(false);
        }

        while (ctx.lookahead() != 0
                && !(quote != 0
                ? ctx.lookahead() == quote || ctx.lookahead() == '\r' || ctx.lookahead() == '\n'
                : isSpace(ctx.lookahead()))) {
            if (ctx.lookahead() == '\\') {
                ctx.advance(false);
                if (ctx.lookahead() == 0) {
                    return false;
                }
            }
            empty = false;
            unquotedWord.push(ctx.lookahead());
            ctx.advance(false);
        }
        unquotedWord.push(0);

        if (quote != 0 && ctx.lookahead() == quote) {
            ctx.advance(false);
        }

        return !empty;
    }

    /**
     * {@code scan_heredoc_start}: skips leading whitespace, records whether the
     * delimiter is quoted (a quoted delimiter disables expansion in the body)
     * and consumes the delimiter word.
     */
    static boolean scanHeredocStart(Heredoc heredoc, ExternalScanContext ctx) {
        while (isSpace(ctx.lookahead())) {
            ctx.advance(true);
        }

        ctx.setResultSymbol(HEREDOC_START);
        heredoc.isRaw = ctx.lookahead() == '\'' || ctx.lookahead() == '"' || ctx.lookahead() == '\\';

        boolean foundDelimiter = advanceWord(ctx, heredoc.delimiter);
        if (!foundDelimiter) {
            heredoc.delimiter.clear();
            return false;
        }
        return foundDelimiter;
    }

    /**
     * {@code scan_heredoc_end_identifier}: checks whether the leading word on
     * this line equals the heredoc delimiter.
     *
     * <p><b>Recorded deviation.</b> The C loop tests
     * {@code array_get(&delimiter, size) == lookahead} <em>before</em>
     * {@code current_leading_word.size < delimiter.size}, so on the iteration
     * that has already consumed the whole delimiter it reads one byte past the
     * end of the buffer (undefined behaviour upstream). The bounds test is
     * hoisted here; the only behavioural difference is in the UB case, where
     * the C result is arbitrary anyway.</p>
     */
    static boolean scanHeredocEndIdentifier(Heredoc heredoc, ExternalScanContext ctx) {
        heredoc.currentLeadingWord.clear();
        int size = 0;
        if (heredoc.delimiter.size() > 0) {
            while (ctx.lookahead() != 0 && ctx.lookahead() != '\n'
                    && heredoc.currentLeadingWord.size() < heredoc.delimiter.size()
                    && (heredoc.delimiter.at(size) & 0xFF) == ctx.lookahead()) {
                heredoc.currentLeadingWord.push(ctx.lookahead());
                ctx.advance(false);
                size++;
            }
        }
        heredoc.currentLeadingWord.push(0);
        if (heredoc.delimiter.size() == 0) {
            return false;
        }
        return heredoc.currentLeadingWord.cStringEquals(heredoc.delimiter);
    }

    /**
     * {@code scan_heredoc_content}: the body/end scanner shared by the
     * "beginning" pair ({@code HEREDOC_BODY_BEGINNING} / {@code SIMPLE_HEREDOC_BODY})
     * and the "started" pair ({@code HEREDOC_CONTENT} / {@code HEREDOC_END}).
     */
    static boolean scanHeredocContent(BashScannerState state, ExternalScanContext ctx,
                                      int middleType, int endType) {
        boolean didAdvance = false;
        Heredoc heredoc = state.lastHeredoc();

        for (;;) {
            switch (ctx.lookahead()) {
                case 0: {
                    if (ctx.eof() && didAdvance) {
                        heredoc.reset();
                        ctx.setResultSymbol(endType);
                        return true;
                    }
                    return false;
                }

                case '\\': {
                    didAdvance = true;
                    ctx.advance(false);
                    ctx.advance(false);
                    break;
                }

                case '$': {
                    if (heredoc.isRaw) {
                        didAdvance = true;
                        ctx.advance(false);
                        break;
                    }
                    if (didAdvance) {
                        ctx.markEnd();
                        ctx.setResultSymbol(middleType);
                        heredoc.started = true;
                        ctx.advance(false);
                        if (isAlpha(ctx.lookahead()) || ctx.lookahead() == '{' || ctx.lookahead() == '(') {
                            return true;
                        }
                        break;
                    }
                    if (middleType == HEREDOC_BODY_BEGINNING && ctx.getColumn() == 0) {
                        ctx.setResultSymbol(middleType);
                        heredoc.started = true;
                        return true;
                    }
                    return false;
                }

                case '\n': {
                    if (!didAdvance) {
                        ctx.advance(true);
                    } else {
                        ctx.advance(false);
                    }
                    didAdvance = true;
                    if (heredoc.allowsIndent) {
                        while (isSpace(ctx.lookahead())) {
                            ctx.advance(false);
                        }
                    }
                    // middleType != endType in both call pairs, so the C test
                    // `result_symbol == HEREDOC_END` after the assignment above
                    // is exactly `!started && endType == HEREDOC_END`.
                    int symbol = heredoc.started ? middleType : endType;
                    ctx.setResultSymbol(symbol);
                    ctx.markEnd();
                    if (scanHeredocEndIdentifier(heredoc, ctx)) {
                        if (symbol == HEREDOC_END) {
                            state.popHeredoc();
                        }
                        return true;
                    }
                    break;
                }

                default: {
                    if (ctx.getColumn() == 0) {
                        // an alternative is to check the starting column of the
                        // heredoc body and track that statefully
                        while (isSpace(ctx.lookahead())) {
                            if (didAdvance) {
                                ctx.advance(false);
                            } else {
                                ctx.advance(true);
                            }
                        }
                        if (endType != SIMPLE_HEREDOC_BODY) {
                            ctx.setResultSymbol(middleType);
                            if (scanHeredocEndIdentifier(heredoc, ctx)) {
                                return true;
                            }
                        }
                        if (endType == SIMPLE_HEREDOC_BODY) {
                            ctx.setResultSymbol(endType);
                            ctx.markEnd();
                            if (scanHeredocEndIdentifier(heredoc, ctx)) {
                                return true;
                            }
                        }
                    }
                    didAdvance = true;
                    ctx.advance(false);
                    break;
                }
            }
        }
    }

    // ---- scan() regions ---------------------------------------------------

    /** Region for {@code HEREDOC_BODY_BEGINNING} / {@code SIMPLE_HEREDOC_BODY}. */
    static int regionBodyBeginning(BashScannerState state, ExternalScanContext ctx, boolean[] valid) {
        if ((valid[HEREDOC_BODY_BEGINNING] || valid[SIMPLE_HEREDOC_BODY])
                && state.heredocCount() > 0
                && !state.lastHeredoc().started
                && !BashScanner.inErrorRecovery(valid)) {
            return scanHeredocContent(state, ctx, HEREDOC_BODY_BEGINNING, SIMPLE_HEREDOC_BODY)
                    ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
        }
        return BashScanFlow.CONTINUE;
    }

    /** Region for {@code HEREDOC_END}. */
    static int regionHeredocEnd(BashScannerState state, ExternalScanContext ctx, boolean[] valid) {
        if (valid[HEREDOC_END] && state.heredocCount() > 0) {
            Heredoc heredoc = state.lastHeredoc();
            if (scanHeredocEndIdentifier(heredoc, ctx)) {
                // The C original frees current_leading_word and delimiter here
                // (array_delete) right before popping the entry; managed memory
                // makes the explicit free unnecessary and the entry is dropped.
                state.popHeredoc();
                ctx.setResultSymbol(HEREDOC_END);
                return BashScanFlow.MATCHED;
            }
        }
        return BashScanFlow.CONTINUE;
    }

    /** Region for {@code HEREDOC_CONTENT}. */
    static int regionHeredocContent(BashScannerState state, ExternalScanContext ctx, boolean[] valid) {
        if (valid[HEREDOC_CONTENT] && state.heredocCount() > 0
                && state.lastHeredoc().started
                && !BashScanner.inErrorRecovery(valid)) {
            return scanHeredocContent(state, ctx, HEREDOC_CONTENT, HEREDOC_END)
                    ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
        }
        return BashScanFlow.CONTINUE;
    }

    /** Region for {@code HEREDOC_START}. */
    static int regionHeredocStart(BashScannerState state, ExternalScanContext ctx, boolean[] valid) {
        if (valid[HEREDOC_START] && !BashScanner.inErrorRecovery(valid) && state.heredocCount() > 0) {
            return scanHeredocStart(state.lastHeredoc(), ctx)
                    ? BashScanFlow.MATCHED : BashScanFlow.NO_MATCH;
        }
        return BashScanFlow.CONTINUE;
    }
}
