package io.nop.treesitter.scanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Faithful Java translation of tree-sitter-c-sharp's {@code scanner.c}
 * (tree-sitter v0.25.8; external tokens: OPT_SEMI, INTERPOLATION_REGULAR_START,
 * INTERPOLATION_VERBATIM_START, INTERPOLATION_RAW_START, INTERPOLATION_START_QUOTE,
 * INTERPOLATION_END_QUOTE, INTERPOLATION_OPEN_BRACE, INTERPOLATION_CLOSE_BRACE,
 * INTERPOLATION_STRING_CONTENT, RAW_STRING_START, RAW_STRING_END,
 * RAW_STRING_CONTENT, LAMBDA_PAREN_OPEN) including the interpolation stack
 * (4-byte-per-level serialize layout) and the C# 14 simple-lambda speculative
 * parameter-list scan with its NO_PAREN / FAILED_AFTER_PAREN / SUCCESS
 * rewind contract.
 *
 * <p>Character classes follow the C locale (ASCII), matching the C
 * iswalpha/iswalnum/iswspace behavior.
 */
public final class CSharpScanner implements ExternalScanner {

    // external token ordinals (scanner.c enum TokenType order)
    private static final int OPT_SEMI = 0;
    private static final int INTERPOLATION_REGULAR_START = 1;
    private static final int INTERPOLATION_VERBATIM_START = 2;
    private static final int INTERPOLATION_RAW_START = 3;
    private static final int INTERPOLATION_START_QUOTE = 4;
    private static final int INTERPOLATION_END_QUOTE = 5;
    private static final int INTERPOLATION_OPEN_BRACE = 6;
    private static final int INTERPOLATION_CLOSE_BRACE = 7;
    private static final int INTERPOLATION_STRING_CONTENT = 8;
    private static final int RAW_STRING_START = 9;
    private static final int RAW_STRING_END = 10;
    private static final int RAW_STRING_CONTENT = 11;
    private static final int LAMBDA_PAREN_OPEN = 12;

    // StringType bits
    private static final int REGULAR = 1 << 0;
    private static final int VERBATIM = 1 << 1;
    private static final int RAW = 1 << 2;

    private static final int LAMBDA_SCAN_NO_PAREN = 0;
    private static final int LAMBDA_SCAN_FAILED_AFTER_PAREN = 1;
    private static final int LAMBDA_SCAN_SUCCESS = 2;

    private static final class Interpolation {
        int dollarCount;
        int openBraceCount;
        int quoteCount;
        int stringType;

        boolean isRegular() {
            return (stringType & REGULAR) != 0;
        }

        boolean isVerbatim() {
            return (stringType & VERBATIM) != 0;
        }

        boolean isRaw() {
            return (stringType & RAW) != 0;
        }
    }

    private int quoteCount;
    private final List<Interpolation> interpolationStack = new ArrayList<>();

    public CSharpScanner() {
        deserialize(new byte[0]);
    }

    private static boolean isIdStart(int c) {
        return c == '_' || iswalpha(c);
    }

    private static boolean isIdContinue(int c) {
        return c == '_' || iswdigit(c) || iswalpha(c);
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
        int braceAdvanced = 0;
        int localQuoteCount = 0;
        boolean didAdvance = false;

        if (validSymbols[LAMBDA_PAREN_OPEN]) {
            switch (scanLambdaParenOpen(ctx)) {
                case LAMBDA_SCAN_SUCCESS:
                    return true;
                case LAMBDA_SCAN_FAILED_AFTER_PAREN:
                    // The cursor advanced past '(' — force a rewind by failing.
                    return false;
                default:
                    break; // NO_PAREN: lexer untouched; fall through
            }
        }

        // error recovery, gives better trees this way
        if (validSymbols[OPT_SEMI] && validSymbols[INTERPOLATION_REGULAR_START]) {
            return false;
        }

        if (validSymbols[OPT_SEMI]) {
            ctx.setResultSymbol(OPT_SEMI);
            if (ctx.lookahead() == ';') {
                ctx.advance(false);
            }
            return true;
        }

        if (validSymbols[RAW_STRING_START]) {
            while (iswspace(ctx.lookahead())) {
                ctx.advance(true);
            }
            if (ctx.lookahead() == '"') {
                while (ctx.lookahead() == '"') {
                    ctx.advance(false);
                    localQuoteCount++;
                }
                if (localQuoteCount >= 3) {
                    ctx.setResultSymbol(RAW_STRING_START);
                    quoteCount = localQuoteCount;
                    return true;
                }
            }
        }

        if (validSymbols[RAW_STRING_END] && ctx.lookahead() == '"') {
            while (ctx.lookahead() == '"') {
                ctx.advance(false);
                localQuoteCount++;
            }
            if (localQuoteCount == quoteCount) {
                ctx.setResultSymbol(RAW_STRING_END);
                quoteCount = 0;
                return true;
            }
            didAdvance = localQuoteCount > 0;
        }

        if (validSymbols[RAW_STRING_CONTENT]) {
            while (ctx.lookahead() != 0 && !ctx.eof()) {
                if (ctx.lookahead() == '"') {
                    ctx.markEnd();
                    localQuoteCount = 0;
                    while (ctx.lookahead() == '"') {
                        ctx.advance(false);
                        localQuoteCount++;
                    }
                    if (localQuoteCount == quoteCount) {
                        ctx.setResultSymbol(RAW_STRING_CONTENT);
                        return true;
                    }
                }
                ctx.advance(false);
                didAdvance = true;
            }
            ctx.markEnd();
            ctx.setResultSymbol(RAW_STRING_CONTENT);
            return true;
        }

        if (validSymbols[INTERPOLATION_REGULAR_START] || validSymbols[INTERPOLATION_VERBATIM_START]
                || validSymbols[INTERPOLATION_RAW_START]) {
            while (iswspace(ctx.lookahead())) {
                ctx.advance(true);
            }
            int dollarAdvanced = 0;
            boolean isVerbatim = false;
            if (ctx.lookahead() == '@') {
                isVerbatim = true;
                ctx.advance(false);
            }
            while (ctx.lookahead() == '$' && localQuoteCount == 0) {
                ctx.advance(false);
                dollarAdvanced++;
            }
            if (dollarAdvanced > 0 && (ctx.lookahead() == '"' || ctx.lookahead() == '@')) {
                ctx.setResultSymbol(INTERPOLATION_REGULAR_START);
                Interpolation interpolation = new Interpolation();
                interpolation.dollarCount = dollarAdvanced;
                interpolation.openBraceCount = 0;
                interpolation.quoteCount = 0;
                interpolation.stringType = 0;
                if (isVerbatim || ctx.lookahead() == '@') {
                    if (ctx.lookahead() == '@') {
                        ctx.advance(false);
                        isVerbatim = true;
                    }
                    ctx.setResultSymbol(INTERPOLATION_VERBATIM_START);
                    interpolation.stringType = VERBATIM;
                }
                ctx.markEnd();
                ctx.advance(false);
                if (ctx.lookahead() == '"' && !isVerbatim) {
                    ctx.advance(false);
                    if (ctx.lookahead() == '"') {
                        ctx.setResultSymbol(INTERPOLATION_RAW_START);
                        interpolation.stringType |= RAW;
                        interpolationStack.add(interpolation);
                    }
                    // 1 or 3+ quotes push an interpolation; 2 quotes is an empty string
                } else {
                    interpolation.stringType |= REGULAR;
                    interpolationStack.add(interpolation);
                }
                return true;
            }
        }

        if (validSymbols[INTERPOLATION_START_QUOTE] && !interpolationStack.isEmpty()) {
            Interpolation current = interpolationStack.get(interpolationStack.size() - 1);
            if (current.isVerbatim() || current.isRegular()) {
                if (ctx.lookahead() == '"') {
                    ctx.advance(false);
                    current.quoteCount++;
                }
            } else {
                while (ctx.lookahead() == '"') {
                    ctx.advance(false);
                    current.quoteCount++;
                }
            }
            ctx.setResultSymbol(INTERPOLATION_START_QUOTE);
            return current.quoteCount > 0;
        }

        if (validSymbols[INTERPOLATION_END_QUOTE] && !interpolationStack.isEmpty()) {
            Interpolation current = interpolationStack.get(interpolationStack.size() - 1);
            while (ctx.lookahead() == '"') {
                ctx.advance(false);
                localQuoteCount++;
            }
            if (localQuoteCount == current.quoteCount) {
                ctx.setResultSymbol(INTERPOLATION_END_QUOTE);
                interpolationStack.remove(interpolationStack.size() - 1);
                return true;
            }
            didAdvance = localQuoteCount > 0;
        }

        if (validSymbols[INTERPOLATION_OPEN_BRACE] && !interpolationStack.isEmpty()) {
            Interpolation current = interpolationStack.get(interpolationStack.size() - 1);
            while (ctx.lookahead() == '{' && braceAdvanced < current.dollarCount) {
                ctx.advance(false);
                braceAdvanced++;
            }
            if (braceAdvanced > 0 && braceAdvanced == current.dollarCount
                    && ctx.lookahead() != '{') {
                current.openBraceCount = braceAdvanced;
                ctx.setResultSymbol(INTERPOLATION_OPEN_BRACE);
                return true;
            }
        }

        if (validSymbols[INTERPOLATION_CLOSE_BRACE] && !interpolationStack.isEmpty()) {
            int closeBraceAdvanced = 0;
            Interpolation current = interpolationStack.get(interpolationStack.size() - 1);
            while (iswspace(ctx.lookahead())) {
                ctx.advance(false);
            }
            while (ctx.lookahead() == '}') {
                ctx.advance(false);
                closeBraceAdvanced++;
                if (closeBraceAdvanced == current.openBraceCount) {
                    current.openBraceCount = 0;
                    ctx.setResultSymbol(INTERPOLATION_CLOSE_BRACE);
                    return true;
                }
            }
            return false;
        }

        if (validSymbols[INTERPOLATION_STRING_CONTENT] && !interpolationStack.isEmpty()) {
            ctx.setResultSymbol(INTERPOLATION_STRING_CONTENT);
            Interpolation current = interpolationStack.get(interpolationStack.size() - 1);
            while (ctx.lookahead() != 0 && !ctx.eof()) {
                // top-down approach, first see if it's raw
                if (current.isRaw()) {
                    if (ctx.lookahead() == '"') {
                        ctx.markEnd();
                        ctx.advance(false);
                        if (ctx.lookahead() == '"') {
                            ctx.advance(false);
                            int quoteAdvanced = 2;
                            while (ctx.lookahead() == '"') {
                                quoteAdvanced++;
                                ctx.advance(false);
                            }
                            if (quoteAdvanced == current.quoteCount) {
                                return didAdvance;
                            }
                        }
                    }
                    if (ctx.lookahead() == '{') {
                        ctx.markEnd();
                        while (ctx.lookahead() == '{' && braceAdvanced < current.openBraceCount) {
                            ctx.advance(false);
                            braceAdvanced++;
                        }
                        if (braceAdvanced == current.openBraceCount && ctx.lookahead() != '{') {
                            return didAdvance;
                        }
                    }
                } else if (current.isVerbatim()) {
                    if (ctx.lookahead() == '"') {
                        ctx.markEnd();
                        ctx.advance(false);
                        if (ctx.lookahead() == '"') {
                            ctx.advance(false);
                            continue;
                        }
                        return didAdvance;
                    }
                    if (ctx.lookahead() == '{') {
                        ctx.markEnd();
                        while (ctx.lookahead() == '{' && braceAdvanced < current.openBraceCount) {
                            ctx.advance(false);
                            braceAdvanced++;
                        }
                        if (braceAdvanced == current.openBraceCount && ctx.lookahead() != '{') {
                            return didAdvance;
                        }
                    }
                } else if (current.isRegular()) {
                    if (ctx.lookahead() == '\\' || ctx.lookahead() == '\n' || ctx.lookahead() == '"') {
                        ctx.markEnd();
                        return didAdvance;
                    }
                    if (ctx.lookahead() == '{') {
                        ctx.markEnd();
                        while (ctx.lookahead() == '{' && braceAdvanced < current.openBraceCount) {
                            ctx.advance(false);
                            braceAdvanced++;
                        }
                        if (braceAdvanced == current.openBraceCount && ctx.lookahead() != '{') {
                            return didAdvance;
                        }
                    }
                }
                if (ctx.lookahead() != '{') {
                    braceAdvanced = 0;
                }
                ctx.advance(false);
                didAdvance = true;
            }
            ctx.markEnd();
            return didAdvance;
        }

        return false;
    }

    @Override
    public byte[] serialize() {
        if (interpolationStack.size() * 4 + 2 > 1024) {
            return new byte[0];
        }
        byte[] out = new byte[2 + interpolationStack.size() * 4];
        int size = 0;
        out[size++] = (byte) quoteCount;
        out[size++] = (byte) interpolationStack.size();
        for (Interpolation interpolation : interpolationStack) {
            out[size++] = (byte) interpolation.dollarCount;
            out[size++] = (byte) interpolation.openBraceCount;
            out[size++] = (byte) interpolation.quoteCount;
            out[size++] = (byte) interpolation.stringType;
        }
        return out;
    }

    @Override
    public void deserialize(byte[] state) {
        quoteCount = 0;
        interpolationStack.clear();
        if (state == null || state.length == 0) {
            return;
        }
        int size = 0;
        quoteCount = state[size++] & 0xFF;
        int stackSize = state[size++] & 0xFF;
        for (int i = 0; i < stackSize; i++) {
            Interpolation interpolation = new Interpolation();
            interpolation.dollarCount = state[size++] & 0xFF;
            interpolation.openBraceCount = state[size++] & 0xFF;
            interpolation.quoteCount = state[size++] & 0xFF;
            interpolation.stringType = state[size++] & 0xFF;
            interpolationStack.add(interpolation);
        }
    }

    // ---- LAMBDA_PAREN_OPEN speculative scan -------------------------------

    private static boolean bufEquals(byte[] buf, int n, String kw) {
        if (n != kw.length()) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if ((char) buf[i] != kw.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int consumeIdentifierInto(ExternalScanContext ctx, byte[] buf, int max) {
        int n = 0;
        while (isIdContinue(ctx.lookahead())) {
            if (n < max) {
                buf[n] = (byte) ctx.lookahead();
            }
            n++;
            ctx.advance(false);
        }
        return n;
    }

    private static void skipWsAndComments(ExternalScanContext ctx) {
        for (;;) {
            int c = ctx.lookahead();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                ctx.advance(false);
            } else if (c == '/') {
                ctx.advance(false);
                if (ctx.lookahead() == '/') {
                    while (ctx.lookahead() != 0 && !ctx.eof() && ctx.lookahead() != '\n') {
                        ctx.advance(false);
                    }
                } else if (ctx.lookahead() == '*') {
                    ctx.advance(false);
                    int prev = 0;
                    while (ctx.lookahead() != 0 && !ctx.eof()
                            && !(prev == '*' && ctx.lookahead() == '/')) {
                        prev = ctx.lookahead();
                        ctx.advance(false);
                    }
                    if (ctx.lookahead() == '/') {
                        ctx.advance(false);
                    }
                } else {
                    return;
                }
            } else {
                return;
            }
        }
    }

    private int scanLambdaParenOpen(ExternalScanContext ctx) {
        while (iswspace(ctx.lookahead())) {
            ctx.advance(true);
        }
        if (ctx.lookahead() != '(') {
            return LAMBDA_SCAN_NO_PAREN;
        }
        ctx.advance(false);
        ctx.markEnd();

        boolean sawHardModifier = false;
        boolean expectingElement = true;
        for (;;) {
            skipWsAndComments(ctx);
            int c = ctx.lookahead();
            if (c == 0 || ctx.eof()) {
                return LAMBDA_SCAN_FAILED_AFTER_PAREN;
            }
            if (c == ')') {
                ctx.advance(false);
                skipWsAndComments(ctx);
                if (!sawHardModifier) {
                    return LAMBDA_SCAN_FAILED_AFTER_PAREN;
                }
                if (ctx.lookahead() != '=') {
                    return LAMBDA_SCAN_FAILED_AFTER_PAREN;
                }
                ctx.advance(false);
                if (ctx.lookahead() != '>') {
                    return LAMBDA_SCAN_FAILED_AFTER_PAREN;
                }
                ctx.setResultSymbol(LAMBDA_PAREN_OPEN);
                return LAMBDA_SCAN_SUCCESS;
            }
            if (!expectingElement) {
                if (c != ',') {
                    return LAMBDA_SCAN_FAILED_AFTER_PAREN;
                }
                ctx.advance(false);
                expectingElement = true;
                continue;
            }
            boolean consumedName = false;
            while (!consumedName) {
                skipWsAndComments(ctx);
                if (!isIdStart(ctx.lookahead())) {
                    return LAMBDA_SCAN_FAILED_AFTER_PAREN;
                }
                byte[] buf = new byte[9];
                int n = consumeIdentifierInto(ctx, buf, buf.length);
                boolean isHardModifier = (n <= 8) && (bufEquals(buf, n, "ref")
                        || bufEquals(buf, n, "out") || bufEquals(buf, n, "in")
                        || bufEquals(buf, n, "readonly"));
                boolean isSoftModifier = (n == 6) && bufEquals(buf, n, "scoped");
                if (isHardModifier) {
                    sawHardModifier = true;
                } else if (isSoftModifier) {
                    // `scoped` counts only when followed by a hard modifier;
                    // the final check below rejects scoped-only lists.
                } else {
                    consumedName = true;
                }
            }
            expectingElement = false;
        }
    }
}
