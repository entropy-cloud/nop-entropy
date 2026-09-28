package io.nop.treesitter.scanner;

/**
 * Faithful Java translation of tree-sitter-rust's {@code scanner.c}
 * (tree-sitter v0.25.8; external tokens: STRING_CONTENT, STRING_CLOSE,
 * RAW_STRING_LITERAL_START, RAW_STRING_LITERAL_CONTENT, RAW_STRING_LITERAL_END,
 * FLOAT_LITERAL, BLOCK_OUTER_DOC_MARKER, BLOCK_INNER_DOC_MARKER,
 * BLOCK_COMMENT_CONTENT, LINE_DOC_CONTENT, ERROR_SENTINEL) including the
 * raw-string hash-count state and the 1-byte serialize/deserialize layout.
 *
 * <p>Gating order and fall-through contracts mirror the C source: the
 * ERROR_SENTINEL bail-out first; any block-comment token short-circuits into
 * the comment state machine; LINE_DOC_CONTENT is checked before whitespace
 * skipping; {@code process_string} returning false falls through so
 * STRING_CLOSE can consume the quote. Character classes follow the C locale
 * (ASCII digits/letters/space), not the full Unicode {@code Character.is*}
 * sets.
 */
public final class RustScanner implements ExternalScanner {

    // external token ordinals (scanner.c enum TokenType order)
    private static final int STRING_CONTENT = 0;
    private static final int STRING_CLOSE = 1;
    private static final int RAW_STRING_LITERAL_START = 2;
    private static final int RAW_STRING_LITERAL_CONTENT = 3;
    private static final int RAW_STRING_LITERAL_END = 4;
    private static final int FLOAT_LITERAL = 5;
    private static final int BLOCK_OUTER_DOC_MARKER = 6;
    private static final int BLOCK_INNER_DOC_MARKER = 7;
    private static final int BLOCK_COMMENT_CONTENT = 8;
    private static final int LINE_DOC_CONTENT = 9;
    private static final int ERROR_SENTINEL = 10;

    // BlockCommentState
    private static final int LEFT_FORWARD_SLASH = 0;
    private static final int LEFT_ASTERISK = 1;
    private static final int CONTINUING = 2;

    private int openingHashCount;

    public RustScanner() {
        deserialize(new byte[0]);
    }

    private static boolean isNumChar(int c) {
        return c == '_' || iswdigit(c);
    }

    private static boolean iswdigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean iswalpha(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean iswspace(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == 0x0C || c == '\r';
    }

    @Override
    public boolean scan(ExternalScanContext ctx, boolean[] validSymbols) {
        // scanner.c: error recovery marks every token valid; nothing to recover
        // here, so bail out immediately (ERROR_SENTINEL contract).
        if (validSymbols[ERROR_SENTINEL]) {
            return false;
        }

        if (validSymbols[BLOCK_COMMENT_CONTENT] || validSymbols[BLOCK_INNER_DOC_MARKER]
                || validSymbols[BLOCK_OUTER_DOC_MARKER]) {
            return processBlockComment(ctx, validSymbols);
        }

        if (validSymbols[STRING_CONTENT] && !validSymbols[FLOAT_LITERAL]) {
            if (processString(ctx)) {
                return true;
            }
            // processString returns false when the next char is '"' or '\' (no
            // content to emit). Fall through so STRING_CLOSE can consume the '"'.
        }

        if (validSymbols[STRING_CLOSE] && ctx.lookahead() == '"') {
            ctx.advance(false);
            ctx.setResultSymbol(STRING_CLOSE);
            ctx.markEnd();
            return true;
        }

        if (validSymbols[LINE_DOC_CONTENT]) {
            return processLineDocContent(ctx);
        }

        while (iswspace(ctx.lookahead())) {
            ctx.advance(true);
        }

        if (validSymbols[RAW_STRING_LITERAL_START]
                && (ctx.lookahead() == 'r' || ctx.lookahead() == 'b' || ctx.lookahead() == 'c')) {
            return scanRawStringStart(ctx);
        }

        if (validSymbols[RAW_STRING_LITERAL_CONTENT]) {
            return scanRawStringContent(ctx);
        }

        if (validSymbols[RAW_STRING_LITERAL_END] && ctx.lookahead() == '"') {
            return scanRawStringEnd(ctx);
        }

        if (validSymbols[FLOAT_LITERAL] && iswdigit(ctx.lookahead())) {
            return processFloatLiteral(ctx);
        }

        return false;
    }

    @Override
    public byte[] serialize() {
        return new byte[]{(byte) openingHashCount};
    }

    @Override
    public void deserialize(byte[] state) {
        openingHashCount = 0;
        if (state != null && state.length == 1) {
            openingHashCount = state[0] & 0xFF;
        }
    }

    private boolean processString(ExternalScanContext ctx) {
        boolean hasContent = false;
        for (;;) {
            if (ctx.lookahead() == '"' || ctx.lookahead() == '\\') {
                break;
            }
            if (ctx.eof()) {
                return false;
            }
            hasContent = true;
            ctx.advance(false);
        }
        ctx.setResultSymbol(STRING_CONTENT);
        ctx.markEnd();
        return hasContent;
    }

    private boolean scanRawStringStart(ExternalScanContext ctx) {
        if (ctx.lookahead() == 'b' || ctx.lookahead() == 'c') {
            ctx.advance(false);
        }
        if (ctx.lookahead() != 'r') {
            return false;
        }
        ctx.advance(false);

        int hashCount = 0;
        while (ctx.lookahead() == '#') {
            ctx.advance(false);
            hashCount = (hashCount + 1) & 0xFF;
        }

        if (ctx.lookahead() != '"') {
            return false;
        }
        ctx.advance(false);
        openingHashCount = hashCount;

        ctx.setResultSymbol(RAW_STRING_LITERAL_START);
        return true;
    }

    private boolean scanRawStringContent(ExternalScanContext ctx) {
        for (;;) {
            if (ctx.eof()) {
                return false;
            }
            if (ctx.lookahead() == '"') {
                ctx.markEnd();
                ctx.advance(false);
                int hashCount = 0;
                while (ctx.lookahead() == '#' && hashCount < openingHashCount) {
                    ctx.advance(false);
                    hashCount++;
                }
                if (hashCount == openingHashCount) {
                    ctx.setResultSymbol(RAW_STRING_LITERAL_CONTENT);
                    return true;
                }
            } else {
                ctx.advance(false);
            }
        }
    }

    private boolean scanRawStringEnd(ExternalScanContext ctx) {
        ctx.advance(false);
        for (int i = 0; i < openingHashCount; i++) {
            ctx.advance(false);
        }
        ctx.setResultSymbol(RAW_STRING_LITERAL_END);
        return true;
    }

    private boolean processFloatLiteral(ExternalScanContext ctx) {
        ctx.setResultSymbol(FLOAT_LITERAL);

        ctx.advance(false);
        while (isNumChar(ctx.lookahead())) {
            ctx.advance(false);
        }

        boolean hasFraction = false;
        boolean hasExponent = false;

        if (ctx.lookahead() == '.') {
            hasFraction = true;
            ctx.advance(false);
            if (iswalpha(ctx.lookahead())) {
                // The dot is followed by a letter: 1.max(2) => not a float but an integer
                return false;
            }
            if (ctx.lookahead() == '.') {
                return false;
            }
            while (isNumChar(ctx.lookahead())) {
                ctx.advance(false);
            }
        }

        ctx.markEnd();

        if (ctx.lookahead() == 'e' || ctx.lookahead() == 'E') {
            hasExponent = true;
            ctx.advance(false);
            if (ctx.lookahead() == '+' || ctx.lookahead() == '-') {
                ctx.advance(false);
            }
            if (!isNumChar(ctx.lookahead())) {
                return true;
            }
            ctx.advance(false);
            while (isNumChar(ctx.lookahead())) {
                ctx.advance(false);
            }
            ctx.markEnd();
        }

        if (!hasExponent && !hasFraction) {
            return false;
        }

        int lookahead = ctx.lookahead();
        if (lookahead != 'u' && lookahead != 'i' && lookahead != 'f') {
            return true;
        }
        ctx.advance(false);
        if (!iswdigit(ctx.lookahead())) {
            return true;
        }
        while (iswdigit(ctx.lookahead())) {
            ctx.advance(false);
        }
        ctx.markEnd();
        return true;
    }

    private boolean processLineDocContent(ExternalScanContext ctx) {
        ctx.setResultSymbol(LINE_DOC_CONTENT);
        for (;;) {
            if (ctx.eof()) {
                return true;
            }
            if (ctx.lookahead() == '\n') {
                // Include the newline in the doc content node.
                ctx.advance(false);
                return true;
            }
            ctx.advance(false);
        }
    }

    private boolean processBlockComment(ExternalScanContext ctx, boolean[] validSymbols) {
        char first = (char) ctx.lookahead();
        if (validSymbols[BLOCK_INNER_DOC_MARKER] && first == '!') {
            ctx.setResultSymbol(BLOCK_INNER_DOC_MARKER);
            ctx.advance(false);
            return true;
        }
        if (validSymbols[BLOCK_OUTER_DOC_MARKER] && first == '*') {
            ctx.advance(false);
            ctx.markEnd();
            // A following / means an empty block comment.
            if (ctx.lookahead() == '/') {
                return false;
            }
            // A following * means this isn't a BLOCK_OUTER_DOC_MARKER
            // (outer doc markers have exactly 2 stars, not 3 or more).
            if (ctx.lookahead() != '*') {
                ctx.setResultSymbol(BLOCK_OUTER_DOC_MARKER);
                return true;
            }
        } else {
            ctx.advance(false);
        }

        if (validSymbols[BLOCK_COMMENT_CONTENT]) {
            int state = CONTINUING;
            int nestingDepth = 1;
            switch (first) {
                case '*':
                    state = LEFT_ASTERISK;
                    if (ctx.lookahead() == '/') {
                        // Empty doc block comment like /*!*/ — no contents, bail.
                        return false;
                    }
                    break;
                case '/':
                    state = LEFT_FORWARD_SLASH;
                    break;
                default:
                    state = CONTINUING;
                    break;
            }

            // An unterminated block comment is consumed to its end: syntax
            // highlighting must see the code before the (missing) close.
            while (!ctx.eof() && nestingDepth != 0) {
                first = (char) ctx.lookahead();
                switch (state) {
                    case LEFT_FORWARD_SLASH:
                        if (first == '*') {
                            nestingDepth++;
                        }
                        state = CONTINUING;
                        break;
                    case LEFT_ASTERISK:
                        if (first == '*') {
                            ctx.markEnd();
                        } else {
                            if (first == '/') {
                                nestingDepth--;
                            }
                            state = CONTINUING;
                        }
                        break;
                    default:
                        ctx.markEnd();
                        if (first == '/') {
                            state = LEFT_FORWARD_SLASH;
                        } else if (first == '*') {
                            state = LEFT_ASTERISK;
                        }
                        break;
                }
                ctx.advance(false);
                if (first == '/' && nestingDepth != 0) {
                    ctx.markEnd();
                }
            }
            ctx.setResultSymbol(BLOCK_COMMENT_CONTENT);
            return true;
        }

        return false;
    }
}
