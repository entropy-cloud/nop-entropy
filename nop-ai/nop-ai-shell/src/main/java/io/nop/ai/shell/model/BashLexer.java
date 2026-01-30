package io.nop.ai.shell.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bash词法分析器
 * 负责将命令行字符串分解为token序列
 */
public class BashLexer {

    private final String input;
    private int position;
    private final int length;

    private static final Pattern[] PATTERNS = {
            Pattern.compile(">>"),
            Pattern.compile("&&"),
            Pattern.compile("\\|\\|"),
            Pattern.compile(";"),
            Pattern.compile("<"),
            Pattern.compile(">"),
            Pattern.compile("&"),
            Pattern.compile("\\("),
            Pattern.compile("\\)"),
            Pattern.compile("\\{"),
            Pattern.compile("\\}"),
            Pattern.compile("[0-9]+"),
            Pattern.compile("\\$?[a-zA-Z_][a-zA-Z0-9_]*"),
            Pattern.compile("'[^']*'"),
            Pattern.compile("\"[^\"]*\"")
    };

    public BashLexer(String input) {
        this.input = input;
        this.length = input.length();
        this.position = 0;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (position < length) {
            skipWhitespace();

            if (position >= length) {
                break;
            }

            char ch = input.charAt(position);

            Token token = tryMatchOperators();
            if (token != null) {
                tokens.add(token);
                position += token.value().length();
                continue;
            }

            token = tryMatchPunctuation();
            if (token != null) {
                tokens.add(token);
                position += token.value().length();
                continue;
            }

            token = tryMatchQuoted();
            if (token != null) {
                tokens.add(token);
                position += token.value().length();
                continue;
            }

            token = tryMatchWord();
            if (token != null) {
                tokens.add(token);
                position += token.value().length();
                continue;
            }

            position++;
        }

        tokens.add(new Token(TokenType.EOF, "", position));
        return tokens;
    }

    private void skipWhitespace() {
        while (position < length && Character.isWhitespace(input.charAt(position))) {
            position++;
        }
    }

    private Token tryMatchOperators() {
        int currentPos = position;

        if (position + 2 <= length) {
            String twoChar = input.substring(position, position + 2);

            if ("&&".equals(twoChar)) {
                return new Token(TokenType.AND, twoChar, currentPos);
            }
            if ("||".equals(twoChar)) {
                return new Token(TokenType.OR, twoChar, currentPos);
            }
            if (">>".equals(twoChar) || ">>".equals(twoChar)) {
                return new Token(TokenType.REDIRECT_APPEND, twoChar, currentPos);
            }
            if ("<&".equals(twoChar) || ">&".equals(twoChar)) {
                return new Token(TokenType.REDIRECT_FD_OUTPUT, twoChar, currentPos);
            }
            if ("&>".equals(twoChar) || "&>>".equals(twoChar)) {
                return new Token(TokenType.REDIRECT_MERGE, twoChar, currentPos);
            }
            if ("<<".equals(twoChar)) {
                return new Token(TokenType.REDIRECT_HERE_DOC, twoChar, currentPos);
            }
            if ("<<<".equals(twoChar)) {
                return new Token(TokenType.REDIRECT_HERE_STRING, twoChar, currentPos);
            }
        }

        if (position + 3 <= length) {
            String threeChar = input.substring(position, position + 3);

            if ("<<<".equals(threeChar)) {
                return new Token(TokenType.REDIRECT_HERE_STRING, threeChar, currentPos);
            }
            if ("&>>".equals(threeChar)) {
                return new Token(TokenType.REDIRECT_MERGE_APPEND, threeChar, currentPos);
            }
        }

        return null;
    }

    private Token tryMatchPunctuation() {
        if (position >= length) return null;

        char ch = input.charAt(position);

        if (ch == '|') {
            return new Token(TokenType.PIPE, String.valueOf(ch), position);
        }
        if (ch == ';') {
            return new Token(TokenType.SEMICOLON, String.valueOf(ch), position);
        }
        if (ch == '(') {
            return new Token(TokenType.LEFT_PAREN, String.valueOf(ch), position);
        }
        if (ch == ')') {
            return new Token(TokenType.RIGHT_PAREN, String.valueOf(ch), position);
        }
        if (ch == '{') {
            return new Token(TokenType.LEFT_BRACE, String.valueOf(ch), position);
        }
        if (ch == '}') {
            return new Token(TokenType.RIGHT_BRACE, String.valueOf(ch), position);
        }
        if (ch == '&') {
            return new Token(TokenType.BACKGROUND, String.valueOf(ch), position);
        }
        if (ch == '<') {
            return new Token(TokenType.REDIRECT_INPUT, String.valueOf(ch), position);
        }
        if (ch == '>') {
            return new Token(TokenType.REDIRECT_OUTPUT, String.valueOf(ch), position);
        }

        return null;
    }

    private Token tryMatchQuoted() {
        if (position >= length) return null;

        char ch = input.charAt(position);

        if (ch == '\'') {
            return matchSingleQuoted();
        }
        if (ch == '"') {
            return matchDoubleQuoted();
        }

        return null;
    }

    private Token matchSingleQuoted() {
        int start = position;
        int pos = position + 1;

        while (pos < length) {
            char ch = input.charAt(pos);

            if (ch == '\'') {
                return new Token(TokenType.QUOTED_SINGLE, input.substring(start, pos + 1), start);
            }
            if (ch == '\\') {
                pos++;
            }
            pos++;
        }

        return new Token(TokenType.QUOTED_SINGLE, input.substring(start, pos), start);
    }

    private Token matchDoubleQuoted() {
        int start = position;
        int pos = position + 1;

        while (pos < length) {
            char ch = input.charAt(pos);

            if (ch == '"') {
                return new Token(TokenType.QUOTED_DOUBLE, input.substring(start, pos + 1), start);
            }
            if (ch == '\\') {
                pos++;
            }
            pos++;
        }

        return new Token(TokenType.QUOTED_DOUBLE, input.substring(start, pos), start);
    }

    private Token tryMatchWord() {
        if (position >= length) return null;

        int start = position;
        int pos = position;

        while (pos < length) {
            char ch = input.charAt(pos);

            if (Character.isWhitespace(ch) || isOperatorChar(ch) || isPunctuationChar(ch)) {
                break;
            }

            pos++;
        }

        if (start == pos) {
            return null;
        }

        String word = input.substring(start, pos);

        if (word.matches("[0-9]+")) {
            return new Token(TokenType.ARGUMENT, word, start);
        }

        return new Token(TokenType.COMMAND, word, start);
    }

    private boolean isOperatorChar(char ch) {
        return ch == '&' || ch == '|' || ch == ';' || ch == '<' || ch == '>';
    }

    private boolean isPunctuationChar(char ch) {
        return ch == '(' || ch == ')' || ch == '{' || ch == '}';
    }

    public int getPosition() {
        return position;
    }
}
