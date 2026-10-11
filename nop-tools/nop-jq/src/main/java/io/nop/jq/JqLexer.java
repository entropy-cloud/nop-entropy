package io.nop.jq;

import io.nop.jq.runtime.JqRuntimeException;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer for jq expressions. Tokenizes jq syntax into a list of tokens.
 *
 * <p>String tokens carry the raw source text between quotes: escape decoding and
 * interpolation (\(...)) splitting happen in the parser, because the raw text must
 * preserve nested quotes and parentheses inside interpolation expressions.
 */
public class JqLexer {
    private final String input;
    private int pos;

    public JqLexer(String input) {
        this.input = input;
    }

    public List<JqToken> tokenize() {
        List<JqToken> tokens = new ArrayList<>();
        while (pos < input.length()) {
            skipWhitespace();
            if (pos >= input.length())
                break;

            char c = input.charAt(pos);
            int start = pos;

            switch (c) {
                case '|' -> {
                    pos++;
                    if (match('=')) {
                        tokens.add(new JqToken(JqTokenType.PIPE_ASSIGN, "|=", start));
                    } else {
                        tokens.add(new JqToken(JqTokenType.PIPE, "|", start));
                    }
                }
                case '.' -> {
                    if (lookAhead(1) == '.') {
                        pos += 2;
                        tokens.add(new JqToken(JqTokenType.DOT_DOT, "..", start));
                    } else if (Character.isDigit(lookAhead(1))) {
                        // .00005 — a number with no integer part
                        tokens.add(readNumber());
                    } else {
                        pos++;
                        tokens.add(new JqToken(JqTokenType.DOT, ".", start));
                    }
                }
                case ',' -> {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.COMMA, ",", start));
                }
                case ':' -> {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.COLON, ":", start));
                }
                case ';' -> {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.SEMICOLON, ";", start));
                }
                case '?' -> {
                    pos++;
                    if (lookAhead(0) == '/' && lookAhead(1) == '/') {
                        pos += 2;
                        tokens.add(new JqToken(JqTokenType.QUESTION_SLASH, "?//", start));
                    } else {
                        tokens.add(new JqToken(JqTokenType.QUESTION, "?", start));
                    }
                }
                case '@' -> {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.AT, "@", start));
                }
                case '(' -> {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.LPAREN, "(", start));
                }
                case ')' -> {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.RPAREN, ")", start));
                }
                case '[' -> {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.LBRACKET, "[", start));
                }
                case ']' -> {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.RBRACKET, "]", start));
                }
                case '{' -> {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.LBRACE, "{", start));
                }
                case '}' -> {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.RBRACE, "}", start));
                }
                case '+' -> {
                    pos++;
                    tokens.add(assignable(JqTokenType.PLUS, JqTokenType.PLUS_ASSIGN, "+", start));
                }
                case '-' -> {
                    pos++;
                    tokens.add(assignable(JqTokenType.MINUS, JqTokenType.MINUS_ASSIGN, "-", start));
                }
                case '*' -> {
                    pos++;
                    tokens.add(assignable(JqTokenType.MULTIPLY, JqTokenType.MULTIPLY_ASSIGN, "*", start));
                }
                case '/' -> {
                    pos++;
                    if (match('/')) {
                        if (match('=')) {
                            tokens.add(new JqToken(JqTokenType.ALTERNATIVE_ASSIGN, "//=", start));
                        } else {
                            tokens.add(new JqToken(JqTokenType.ALTERNATIVE, "//", start));
                        }
                    } else {
                        tokens.add(assignable(JqTokenType.DIVIDE, JqTokenType.DIVIDE_ASSIGN, "/", start));
                    }
                }
                case '%' -> {
                    pos++;
                    tokens.add(assignable(JqTokenType.MODULO, JqTokenType.MODULO_ASSIGN, "%", start));
                }
                case '=' -> {
                    pos++;
                    if (match('=')) {
                        tokens.add(new JqToken(JqTokenType.EQUAL, "==", start));
                    } else {
                        tokens.add(new JqToken(JqTokenType.ASSIGN, "=", start));
                    }
                }
                case '!' -> {
                    pos++;
                    if (!match('=')) {
                        throw new JqRuntimeException("Unexpected character '!' in jq expression");
                    }
                    tokens.add(new JqToken(JqTokenType.NOT_EQUAL, "!=", start));
                }
                case '>' -> {
                    pos++;
                    if (match('=')) {
                        tokens.add(new JqToken(JqTokenType.GREATER_EQ, ">=", start));
                    } else {
                        tokens.add(new JqToken(JqTokenType.GREATER, ">", start));
                    }
                }
                case '<' -> {
                    pos++;
                    if (match('=')) {
                        tokens.add(new JqToken(JqTokenType.LESS_EQ, "<=", start));
                    } else {
                        tokens.add(new JqToken(JqTokenType.LESS, "<", start));
                    }
                }
                case '"', '\'' -> tokens.add(readString());
                case '$' -> tokens.add(new JqToken(JqTokenType.IDENT, readIdent(), start));
                default -> {
                    if (Character.isDigit(c)) {
                        tokens.add(readNumber());
                    } else if (Character.isLetter(c) || c == '_') {
                        String ident = readIdent();
                        tokens.add(new JqToken(keywordType(ident), ident, start));
                    } else {
                        throw new JqRuntimeException(
                                "Unexpected character '" + c + "' in jq expression at position " + start);
                    }
                }
            }
        }
        tokens.add(new JqToken(JqTokenType.EOF, "", pos));
        return tokens;
    }

    private JqToken assignable(JqTokenType base, JqTokenType assign, String text, int start) {
        if (match('=')) {
            return new JqToken(assign, text + "=", start);
        }
        return new JqToken(base, text, start);
    }

    private boolean match(char expected) {
        if (pos < input.length() && input.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private char lookAhead(int offset) {
        int index = pos + offset;
        return index < input.length() ? input.charAt(index) : '\0';
    }

    /**
     * Read a string literal, returning the raw source text between the quotes.
     * The scan is interpolation-aware: after a \( sequence, nested strings and
     * parentheses are consumed verbatim so that quotes inside \(...) do not
     * terminate the string.
     */
    private JqToken readString() {
        char quote = input.charAt(pos);
        int start = pos;
        pos++; // skip opening quote
        int contentStart = pos;
        StringBuilder raw = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != quote) {
            char c = input.charAt(pos);
            if (c == '\\' && pos + 1 < input.length()) {
                char next = input.charAt(pos + 1);
                if (next == '(') {
                    // interpolation: copy verbatim up to the matching ')'
                    raw.append(readInterpolationRaw());
                    continue;
                }
                raw.append(c).append(next);
                pos += 2;
                continue;
            }
            raw.append(c);
            pos++;
        }
        if (pos >= input.length()) {
            throw new JqRuntimeException("Unterminated string starting at position " + start);
        }
        pos++; // skip closing quote
        return new JqToken(JqTokenType.STRING, raw.toString(), start);
    }

    private String readInterpolationRaw() {
        int depth = 1;
        int start = pos;
        pos += 2; // skip \(
        while (pos < input.length() && depth > 0) {
            char c = input.charAt(pos);
            if (c == '"' || c == '\'') {
                readVerbatimString(c);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '\\' && pos + 1 < input.length()) {
                pos++; // skip escaped char verbatim
            }
            pos++;
        }
        if (depth != 0) {
            throw new JqRuntimeException("Unterminated string interpolation starting at position " + start);
        }
        return input.substring(start, pos);
    }

    /** Consume a quoted string inside an interpolation expression without decoding it. */
    private void readVerbatimString(char quote) {
        pos++; // skip opening quote
        while (pos < input.length() && input.charAt(pos) != quote) {
            if (input.charAt(pos) == '\\' && pos + 1 < input.length()) {
                pos++;
            }
            pos++;
        }
        pos++; // skip closing quote
    }

    private JqToken readNumber() {
        int start = pos;
        boolean isFloat = false;
        while (pos < input.length() && Character.isDigit(input.charAt(pos)))
            pos++;
        if (pos < input.length() && input.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos)))
                pos++;
        }
        // exponent: 1e5, 2.5E-3, 9E999999999
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            int save = pos;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-'))
                pos++;
            if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                isFloat = true;
                while (pos < input.length() && Character.isDigit(input.charAt(pos)))
                    pos++;
            } else {
                pos = save; // not an exponent after all
            }
        }
        String text = input.substring(start, pos);
        return new JqToken(isFloat ? JqTokenType.FLOAT : JqTokenType.INTEGER, text, start);
    }

    private String readIdent() {
        int start = pos;
        pos++; // consume $, letter or underscore the caller has peeked
        while (pos < input.length()
                && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
        return input.substring(start, pos);
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos)))
            pos++;
    }

    private JqTokenType keywordType(String ident) {
        return switch (ident) {
            case "select" -> JqTokenType.SELECT;
            case "map" -> JqTokenType.MAP;
            case "reduce" -> JqTokenType.REDUCE;
            case "if" -> JqTokenType.IF;
            case "then" -> JqTokenType.THEN;
            case "else" -> JqTokenType.ELSE;
            case "elif" -> JqTokenType.ELIF;
            case "end" -> JqTokenType.END;
            case "as" -> JqTokenType.AS;
            case "try" -> JqTokenType.TRY;
            case "catch" -> JqTokenType.CATCH;
            case "and" -> JqTokenType.AND;
            case "or" -> JqTokenType.OR;
            case "not" -> JqTokenType.NOT;
            case "null" -> JqTokenType.NULL;
            case "true" -> JqTokenType.TRUE;
            case "false" -> JqTokenType.FALSE;
            case "length" -> JqTokenType.LENGTH;
            case "keys" -> JqTokenType.KEYS;
            case "values" -> JqTokenType.VALUES;
            case "type" -> JqTokenType.TYPE;
            case "empty" -> JqTokenType.EMPTY;
            case "recurse" -> JqTokenType.RECURSE;
            case "limit" -> JqTokenType.LIMIT;
            case "label" -> JqTokenType.LABEL;
            case "break" -> JqTokenType.BREAK;
            case "def" -> JqTokenType.DEF;
            case "import" -> JqTokenType.IMPORT;
            case "module" -> JqTokenType.MODULE;
            case "input" -> JqTokenType.INPUT;
            case "inputs" -> JqTokenType.INPUTS;
            case "debug" -> JqTokenType.DEBUG;
            case "error" -> JqTokenType.ERROR;
            case "env" -> JqTokenType.ENV;
            case "foreach" -> JqTokenType.FOREACH;
            case "until" -> JqTokenType.UNTIL;
            case "while" -> JqTokenType.WHILE;
            default -> JqTokenType.IDENT;
        };
    }
}
