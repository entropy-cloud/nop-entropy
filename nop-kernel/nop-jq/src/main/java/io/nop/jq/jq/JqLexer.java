package io.nop.jq.jq;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer for jq expressions. Tokenizes jq syntax into a list of tokens.
 */
public class JqLexer {
    private final String input;
    private int pos;

    public JqLexer(String input) {
        this.input = input;
        this.pos = 0;
    }

    public List<JqToken> tokenize() {
        List<JqToken> tokens = new ArrayList<>();
        while (pos < input.length()) {
            skipWhitespace();
            if (pos >= input.length())
                break;

            char c = input.charAt(pos);
            int start = pos;

            if (c == '|') {
                pos++;
                if (pos < input.length() && input.charAt(pos) == '=') {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.PIPE_ASSIGN, "|=", start));
                } else {
                    tokens.add(new JqToken(JqTokenType.PIPE, "|", start));
                }
            } else if (c == '.' && pos + 1 < input.length() && input.charAt(pos + 1) == '.') {
                pos += 2;
                tokens.add(new JqToken(JqTokenType.DOT_DOT, "..", start));
            } else if (c == '.') {
                pos++;
                tokens.add(new JqToken(JqTokenType.DOT, ".", start));
            } else if (c == ',') {
                pos++;
                tokens.add(new JqToken(JqTokenType.COMMA, ",", start));
            } else if (c == ':') {
                pos++;
                tokens.add(new JqToken(JqTokenType.COLON, ":", start));
            } else if (c == ';') {
                pos++;
                tokens.add(new JqToken(JqTokenType.SEMICOLON, ";", start));
            } else if (c == '?') {
                pos++;
                tokens.add(new JqToken(JqTokenType.QUESTION, "?", start));
            } else if (c == '@') {
                pos++;
                tokens.add(new JqToken(JqTokenType.AT, "@", start));
            } else if (c == '(') {
                pos++;
                tokens.add(new JqToken(JqTokenType.LPAREN, "(", start));
            } else if (c == ')') {
                pos++;
                tokens.add(new JqToken(JqTokenType.RPAREN, ")", start));
            } else if (c == '[') {
                pos++;
                tokens.add(new JqToken(JqTokenType.LBRACKET, "[", start));
            } else if (c == ']') {
                pos++;
                tokens.add(new JqToken(JqTokenType.RBRACKET, "]", start));
            } else if (c == '{') {
                pos++;
                tokens.add(new JqToken(JqTokenType.LBRACE, "{", start));
            } else if (c == '}') {
                pos++;
                tokens.add(new JqToken(JqTokenType.RBRACE, "}", start));
            } else if (c == '+' && pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                pos += 2;
                tokens.add(new JqToken(JqTokenType.PLUS_ASSIGN, "+=", start));
            } else if (c == '+') {
                pos++;
                tokens.add(new JqToken(JqTokenType.PLUS, "+", start));
            } else if (c == '-' && pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                pos += 2;
                tokens.add(new JqToken(JqTokenType.MINUS_ASSIGN, "-=", start));
            } else if (c == '-') {
                pos++;
                tokens.add(new JqToken(JqTokenType.MINUS, "-", start));
            } else if (c == '*') {
                pos++;
                tokens.add(new JqToken(JqTokenType.MULTIPLY, "*", start));
            } else if (c == '/') {
                pos++;
                tokens.add(new JqToken(JqTokenType.DIVIDE, "/", start));
            } else if (c == '%') {
                pos++;
                tokens.add(new JqToken(JqTokenType.MODULO, "%", start));
            } else if (c == '=' && pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                pos += 2;
                tokens.add(new JqToken(JqTokenType.EQUAL, "==", start));
            } else if (c == '=' && pos + 1 < input.length() && input.charAt(pos + 1) != '=') {
                pos++;
                tokens.add(new JqToken(JqTokenType.ASSIGN, "=", start));
            } else if (c == '!' && pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                pos += 2;
                tokens.add(new JqToken(JqTokenType.NOT_EQUAL, "!=", start));
            } else if (c == '>') {
                pos++;
                if (pos < input.length() && input.charAt(pos) == '=') {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.GREATER_EQ, ">=", start));
                } else {
                    tokens.add(new JqToken(JqTokenType.GREATER, ">", start));
                }
            } else if (c == '<') {
                pos++;
                if (pos < input.length() && input.charAt(pos) == '=') {
                    pos++;
                    tokens.add(new JqToken(JqTokenType.LESS_EQ, "<=", start));
                } else {
                    tokens.add(new JqToken(JqTokenType.LESS, "<", start));
                }
            } else if (c == '\'' || c == '"') {
                tokens.add(readString());
            } else if (Character.isDigit(c)) {
                tokens.add(readNumber());
            } else if (c == '$') {
                tokens.add(new JqToken(JqTokenType.IDENT, readIdent(), start));
            } else if (Character.isLetter(c) || c == '_') {
                String ident = readIdent();
                JqTokenType type = keywordType(ident);
                tokens.add(new JqToken(type, ident, start));
            } else {
                pos++;
                tokens.add(new JqToken(JqTokenType.ERROR, String.valueOf(c), start));
            }
        }
        tokens.add(new JqToken(JqTokenType.EOF, "", pos));
        return tokens;
    }

    private JqToken readString() {
        char quote = input.charAt(pos);
        int start = pos;
        pos++; // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != quote) {
            if (input.charAt(pos) == '\\' && pos + 1 < input.length()) {
                pos++;
                switch (input.charAt(pos)) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '\'': sb.append('\''); break;
                    case '"': sb.append('"'); break;
                    default: sb.append(input.charAt(pos)); break;
                }
            } else {
                sb.append(input.charAt(pos));
            }
            pos++;
        }
        if (pos < input.length())
            pos++; // skip closing quote
        return new JqToken(JqTokenType.STRING, sb.toString(), start);
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
        String text = input.substring(start, pos);
        return new JqToken(isFloat ? JqTokenType.FLOAT : JqTokenType.INTEGER, text, start);
    }

    private String readIdent() {
        int start = pos;
        if (pos < input.length() && input.charAt(pos) == '$')
            pos++;
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_'))
            pos++;
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
