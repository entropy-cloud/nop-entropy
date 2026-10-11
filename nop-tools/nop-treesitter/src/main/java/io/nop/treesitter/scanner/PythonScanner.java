package io.nop.treesitter.scanner;

/**
 * Faithful Java translation of tree-sitter-python's {@code scanner.c}
 * (external tokens: NEWLINE, INDENT, DEDENT, STRING_START, STRING_CONTENT,
 * ESCAPE_INTERPOLATION, STRING_END, COMMENT, CLOSE_PAREN, CLOSE_BRACKET,
 * CLOSE_BRACE, EXCEPT) including the indentation stack, string delimiters and
 * serialize/deserialize state layout.
 */
public final class PythonScanner implements ExternalScanner {

    // external token ordinals (scanner.c enum TokenType order)
    private static final int NEWLINE = 0;
    private static final int INDENT = 1;
    private static final int DEDENT = 2;
    private static final int STRING_START = 3;
    private static final int STRING_CONTENT = 4;
    private static final int ESCAPE_INTERPOLATION = 5;
    private static final int STRING_END = 6;
    private static final int COMMENT = 7;
    private static final int CLOSE_PAREN = 8;
    private static final int CLOSE_BRACKET = 9;
    private static final int CLOSE_BRACE = 10;
    private static final int EXCEPT = 11;

    private static final int SINGLE_QUOTE = 1 << 0;
    private static final int DOUBLE_QUOTE = 1 << 1;
    private static final int BACK_QUOTE = 1 << 2;
    private static final int RAW = 1 << 3;
    private static final int FORMAT = 1 << 4;
    private static final int TRIPLE = 1 << 5;
    private static final int BYTES = 1 << 6;

    private final java.util.ArrayDeque<Integer> indents = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Integer> delimiters = new java.util.ArrayDeque<>();
    private boolean insideInterpolatedString;

    public PythonScanner() {
        deserialize(new byte[0]);
    }

    private static int endCharacter(int delimiter) {
        if ((delimiter & SINGLE_QUOTE) != 0) {
            return '\'';
        }
        if ((delimiter & DOUBLE_QUOTE) != 0) {
            return '"';
        }
        if ((delimiter & BACK_QUOTE) != 0) {
            return '`';
        }
        return 0;
    }

    @Override
    public boolean scan(ExternalScanContext ctx, boolean[] valid) {
        boolean errorRecoveryMode = valid[STRING_CONTENT] && valid[INDENT];
        boolean withinBrackets = valid[CLOSE_BRACE] || valid[CLOSE_PAREN] || valid[CLOSE_BRACKET];

        boolean advancedOnce = false;
        if (valid[ESCAPE_INTERPOLATION] && !delimiters.isEmpty()
                && (ctx.lookahead() == '{' || ctx.lookahead() == '}') && !errorRecoveryMode) {
            int delimiter = delimiters.peek();
            if ((delimiter & FORMAT) != 0) {
                ctx.markEnd();
                boolean isLeftBrace = ctx.lookahead() == '{';
                ctx.advance(false);
                advancedOnce = true;
                if ((ctx.lookahead() == '{' && isLeftBrace) || (ctx.lookahead() == '}' && !isLeftBrace)) {
                    ctx.advance(false);
                    ctx.markEnd();
                    ctx.setResultSymbol(ESCAPE_INTERPOLATION);
                    return true;
                }
                return false;
            }
        }

        if (valid[STRING_CONTENT] && !delimiters.isEmpty() && !errorRecoveryMode) {
            int delimiter = delimiters.peek();
            int endChar = endCharacter(delimiter);
            boolean hasContent = advancedOnce;
            while (ctx.lookahead() != 0) {
                if ((advancedOnce || ctx.lookahead() == '{' || ctx.lookahead() == '}')
                        && (delimiter & FORMAT) != 0) {
                    ctx.markEnd();
                    ctx.setResultSymbol(STRING_CONTENT);
                    return hasContent;
                }
                if (ctx.lookahead() == '\\') {
                    if ((delimiter & RAW) != 0) {
                        ctx.advance(false);
                        if (ctx.lookahead() == endChar || ctx.lookahead() == '\\') {
                            ctx.advance(false);
                        }
                        if (ctx.lookahead() == '\r') {
                            ctx.advance(false);
                            if (ctx.lookahead() == '\n') {
                                ctx.advance(false);
                            }
                        } else if (ctx.lookahead() == '\n') {
                            ctx.advance(false);
                        }
                        continue;
                    }
                    if ((delimiter & BYTES) != 0) {
                        ctx.markEnd();
                        ctx.advance(false);
                        if (ctx.lookahead() == 'N' || ctx.lookahead() == 'u' || ctx.lookahead() == 'U') {
                            ctx.advance(false);
                        } else {
                            ctx.setResultSymbol(STRING_CONTENT);
                            return hasContent;
                        }
                    } else {
                        ctx.markEnd();
                        ctx.setResultSymbol(STRING_CONTENT);
                        return hasContent;
                    }
                } else if (ctx.lookahead() == endChar) {
                    if ((delimiter & TRIPLE) != 0) {
                        ctx.markEnd();
                        ctx.advance(false);
                        if (ctx.lookahead() == endChar) {
                            ctx.advance(false);
                            if (ctx.lookahead() == endChar) {
                                if (hasContent) {
                                    ctx.setResultSymbol(STRING_CONTENT);
                                } else {
                                    ctx.advance(false);
                                    ctx.markEnd();
                                    delimiters.pop();
                                    ctx.setResultSymbol(STRING_END);
                                    insideInterpolatedString = false;
                                }
                                return true;
                            }
                            ctx.markEnd();
                            ctx.setResultSymbol(STRING_CONTENT);
                            return true;
                        }
                        ctx.markEnd();
                        ctx.setResultSymbol(STRING_CONTENT);
                        return true;
                    }
                    if (hasContent) {
                        ctx.setResultSymbol(STRING_CONTENT);
                    } else {
                        ctx.advance(false);
                        delimiters.pop();
                        ctx.setResultSymbol(STRING_END);
                        insideInterpolatedString = false;
                    }
                    ctx.markEnd();
                    return true;
                } else if (ctx.lookahead() == '\n' && hasContent && (delimiter & TRIPLE) == 0) {
                    return false;
                }
                ctx.advance(false);
                hasContent = true;
            }
        }

        ctx.markEnd();

        boolean foundEndOfLine = false;
        int indentLength = 0;
        int firstCommentIndentLength = -1;
        for (;;) {
            if (ctx.lookahead() == '\n') {
                foundEndOfLine = true;
                indentLength = 0;
                ctx.advance(true);
            } else if (ctx.lookahead() == ' ') {
                indentLength++;
                ctx.advance(true);
            } else if (ctx.lookahead() == '\r' || ctx.lookahead() == '\f') {
                indentLength = 0;
                ctx.advance(true);
            } else if (ctx.lookahead() == '\t') {
                indentLength += 8;
                ctx.advance(true);
            } else if (ctx.lookahead() == '#' && (valid[INDENT] || valid[DEDENT]
                    || valid[NEWLINE] || valid[EXCEPT])) {
                if (!foundEndOfLine) {
                    return false;
                }
                if (firstCommentIndentLength == -1) {
                    firstCommentIndentLength = indentLength;
                }
                while (ctx.lookahead() != 0 && ctx.lookahead() != '\n') {
                    ctx.advance(true);
                }
                ctx.advance(true);
                indentLength = 0;
            } else if (ctx.lookahead() == '\\') {
                ctx.advance(true);
                if (ctx.lookahead() == '\r') {
                    ctx.advance(true);
                }
                if (ctx.lookahead() == '\n' || ctx.eof()) {
                    ctx.advance(true);
                } else {
                    return false;
                }
            } else if (ctx.eof()) {
                indentLength = 0;
                foundEndOfLine = true;
                break;
            } else {
                break;
            }
        }

        if (foundEndOfLine && !indents.isEmpty()) {
            int currentIndentLength = indents.peek();

            if (valid[INDENT] && indentLength > currentIndentLength) {
                indents.push(indentLength);
                ctx.setResultSymbol(INDENT);
                return true;
            }

            boolean nextTokIsStringStart =
                    ctx.lookahead() == '"' || ctx.lookahead() == '\'' || ctx.lookahead() == '`';

            if ((valid[DEDENT]
                    || (!valid[NEWLINE] && !(valid[STRING_START] && nextTokIsStringStart)
                            && !withinBrackets))
                    && indentLength < currentIndentLength && !insideInterpolatedString
                    && firstCommentIndentLength < currentIndentLength) {
                indents.pop();
                ctx.setResultSymbol(DEDENT);
                return true;
            }
        }

        if (foundEndOfLine && valid[NEWLINE] && !errorRecoveryMode) {
            ctx.setResultSymbol(NEWLINE);
            return true;
        }

        if (firstCommentIndentLength == -1 && valid[STRING_START]) {
            int delimiter = 0;

            boolean hasFlags = false;
            while (ctx.lookahead() != 0) {
                if (ctx.lookahead() == 'f' || ctx.lookahead() == 'F' || ctx.lookahead() == 't'
                        || ctx.lookahead() == 'T') {
                    delimiter |= FORMAT;
                } else if (ctx.lookahead() == 'r' || ctx.lookahead() == 'R') {
                    delimiter |= RAW;
                } else if (ctx.lookahead() == 'b' || ctx.lookahead() == 'B') {
                    delimiter |= BYTES;
                } else if (ctx.lookahead() != 'u' && ctx.lookahead() != 'U') {
                    break;
                }
                hasFlags = true;
                ctx.advance(false);
            }

            if (ctx.lookahead() == '`') {
                delimiter |= BACK_QUOTE;
                ctx.advance(false);
                ctx.markEnd();
            } else if (ctx.lookahead() == '\'') {
                delimiter |= SINGLE_QUOTE;
                ctx.advance(false);
                ctx.markEnd();
                if (ctx.lookahead() == '\'') {
                    ctx.advance(false);
                    if (ctx.lookahead() == '\'') {
                        ctx.advance(false);
                        ctx.markEnd();
                        delimiter |= TRIPLE;
                    }
                }
            } else if (ctx.lookahead() == '"') {
                delimiter |= DOUBLE_QUOTE;
                ctx.advance(false);
                ctx.markEnd();
                if (ctx.lookahead() == '"') {
                    ctx.advance(false);
                    if (ctx.lookahead() == '"') {
                        ctx.advance(false);
                        ctx.markEnd();
                        delimiter |= TRIPLE;
                    }
                }
            }

            if (endCharacter(delimiter) != 0) {
                delimiters.push(delimiter);
                ctx.setResultSymbol(STRING_START);
                insideInterpolatedString = (delimiter & FORMAT) != 0;
                return true;
            }
            if (hasFlags) {
                return false;
            }
        }

        return false;
    }

    @Override
    public byte[] serialize() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(insideInterpolatedString ? 1 : 0);
        int delimiterCount = Math.min(delimiters.size(), 255);
        out.write(delimiterCount);
        // C memcpy's the delimiter array in storage order (bottom -> top)
        int written = 0;
        for (java.util.Iterator<Integer> it = delimiters.descendingIterator(); it.hasNext()
                && written < delimiterCount; written++) {
            out.write(it.next() & 0xFF);
        }
        // C skips indents[0] (the base) and writes the rest bottom -> top
        java.util.Iterator<Integer> it = indents.descendingIterator();
        it.next(); // base 0
        while (it.hasNext()) {
            int indent = it.next();
            out.write(indent & 0xFF);
            out.write((indent >> 8) & 0xFF);
        }
        return out.toByteArray();
    }

    @Override
    public void deserialize(byte[] state) {
        delimiters.clear();
        indents.clear();
        indents.push(0);
        if (state.length > 0) {
            int size = 0;
            insideInterpolatedString = state[size++] != 0;
            int delimiterCount = state[size++] & 0xFF;
            for (int i = 0; i < delimiterCount; i++) {
                delimiters.push(state[size++] & 0xFF);
            }
            while (size + 1 < state.length) {
                int indentValue = (state[size] & 0xFF) | ((state[size + 1] & 0xFF) << 8);
                size += 2;
                indents.push(indentValue);
            }
        }
    }
}
