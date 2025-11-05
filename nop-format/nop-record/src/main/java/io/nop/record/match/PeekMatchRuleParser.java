package io.nop.record.match;

import java.util.ArrayList;
import java.util.List;

public class PeekMatchRuleParser {

    enum TokenType {
        GET, LPAREN, RPAREN, NUMBER, STRING, HEX, IDENTIFIER, EQUALS, AND, OR, ARROW, COMMA, NOT, STAR, EOF
    }

    static class Token {
        TokenType type;
        String value;
        byte[] bytes; // For HEX type, store the byte array

        Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }

        Token(TokenType type, String value, byte[] bytes) {
            this.type = type;
            this.value = value;
            this.bytes = bytes;
        }

        @Override
        public String toString() {
            return type + "(" + value + ")";
        }
    }

    interface Expr {
        String toString();

        boolean evaluate(IPeekMatchConditionChecker checker);
    }

    static class Condition implements Expr, IPeekMatchCondition {
        int offset;
        int length;
        String operator;
        String value;
        byte[] bytes;

        @Override
        public int getOffset() {
            return offset;
        }

        @Override
        public int getLength() {
            return length;
        }

        public String getOperator() {
            return operator;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public String toString() {
            if (bytes != null) {
                return String.format("GET(%d,%d) %s 0x%s",
                        offset, length, operator, bytesToHex(bytes));
            }
            return String.format("GET(%d,%d) %s '%s'",
                    offset, length, operator, value);
        }

        @Override
        public boolean evaluate(IPeekMatchConditionChecker checker) {
            return checker.matchCondition(this);
        }
    }

    static class WildcardCondition extends Condition {
        @Override
        public String toString() {
            return "*";
        }

        @Override
        public boolean evaluate(IPeekMatchConditionChecker checker) {
            // Wildcard always matches
            return true;
        }
    }

    static class LogicalExpr implements Expr {
        Expr left;
        Expr right;
        String operator;

        @Override
        public String toString() {
            String leftStr = left.toString();
            String rightStr = right.toString();

            if (left instanceof LogicalExpr) {
                leftStr = "(" + leftStr + ")";
            }
            if (right instanceof LogicalExpr) {
                rightStr = "(" + rightStr + ")";
            }

            return leftStr + " " + operator + " " + rightStr;
        }

        @Override
        public boolean evaluate(IPeekMatchConditionChecker checker) {
            boolean leftResult = left.evaluate(checker);
            if ("or".equalsIgnoreCase(operator) && leftResult) {
                return true;
            }
            if ("and".equalsIgnoreCase(operator) && !leftResult) {
                return false;
            }
            return right.evaluate(checker);
        }
    }

    static class NotExpr implements Expr {
        Expr expr;

        @Override
        public String toString() {
            if (expr instanceof Condition) {
                return "!" + expr;
            }
            return "!(" + expr + ")";
        }

        @Override
        public boolean evaluate(IPeekMatchConditionChecker checker) {
            return !expr.evaluate(checker);
        }
    }

    static class Rule {
        Expr condition;
        String resultType;
        boolean isWildcard;

        @Override
        public String toString() {
            return isWildcard ? "* => " + resultType : condition + " => " + resultType;
        }
    }

    static class MatchRule implements IPeekMatchRule {
        List<Rule> rules;

        @Override
        public String match(IPeekMatchConditionChecker checker) {
            if (rules == null || checker == null) {
                return null;
            }

            for (Rule rule : rules) {
                if (rule.isWildcard || rule.condition.evaluate(checker)) {
                    return rule.resultType;
                }
            }

            return null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rules.size(); i++) {
                if (i > 0) {
                    sb.append("\n");
                }
                sb.append(rules.get(i).toString());
            }
            return sb.toString();
        }
    }

    static class Lexer {
        private String input;
        private int pos = 0;
        private List<Token> tokens = new ArrayList<>();

        public Lexer(String input) {
            this.input = input;
            tokenize();
        }

        private void tokenize() {
            while (pos < input.length()) {
                skipWhitespace();
                if (pos >= input.length()) break;

                char ch = input.charAt(pos);

                if (ch == '*') {
                    tokens.add(new Token(TokenType.STAR, "*"));
                    pos++;
                } else if (ch == '(') {
                    tokens.add(new Token(TokenType.LPAREN, "("));
                    pos++;
                } else if (ch == ')') {
                    tokens.add(new Token(TokenType.RPAREN, ")"));
                    pos++;
                } else if (ch == ',') {
                    tokens.add(new Token(TokenType.COMMA, ","));
                    pos++;
                } else if (ch == '\'') {
                    tokens.add(readString());
                } else if (ch == '=' && pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                    tokens.add(new Token(TokenType.EQUALS, "=="));
                    pos += 2;
                } else if (ch == '=' && pos + 1 < input.length() && input.charAt(pos + 1) == '>') {
                    tokens.add(new Token(TokenType.ARROW, "=>"));
                    pos += 2;
                } else if (ch == '!') {
                    tokens.add(new Token(TokenType.NOT, "!"));
                    pos++;
                } else if (ch == '0' && pos + 1 < input.length() && input.charAt(pos + 1) == 'x') {
                    tokens.add(readHex());
                } else if (Character.isDigit(ch)) {
                    tokens.add(readNumber());
                } else if (Character.isLetter(ch) || ch == '_') {
                    tokens.add(readIdentifier());
                } else {
                    throw new IllegalArgumentException("Unexpected character: " + ch + " at position " + pos);
                }
            }
            tokens.add(new Token(TokenType.EOF, ""));
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private Token readString() {
            StringBuilder sb = new StringBuilder();
            pos++; // skip opening quote
            while (pos < input.length() && input.charAt(pos) != '\'') {
                sb.append(input.charAt(pos));
                pos++;
            }
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unterminated string");
            }
            pos++; // skip closing quote
            return new Token(TokenType.STRING, sb.toString());
        }

        private Token readNumber() {
            StringBuilder sb = new StringBuilder();
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                sb.append(input.charAt(pos));
                pos++;
            }
            return new Token(TokenType.NUMBER, sb.toString());
        }

        private Token readHex() {
            StringBuilder sb = new StringBuilder();
            sb.append(input.charAt(pos++)); // '0'
            sb.append(input.charAt(pos++)); // 'x'

            while (pos < input.length() && isHexDigit(input.charAt(pos))) {
                sb.append(input.charAt(pos));
                pos++;
            }

            String hexStr = sb.toString().substring(2);
            byte[] bytes = hexStringToByteArray(hexStr);

            return new Token(TokenType.HEX, sb.toString(), bytes);
        }

        private Token readIdentifier() {
            StringBuilder sb = new StringBuilder();
            while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
                sb.append(input.charAt(pos));
                pos++;
            }
            String value = sb.toString();

            switch (value.toLowerCase()) {
                case "get":
                    return new Token(TokenType.GET, value);
                case "and":
                    return new Token(TokenType.AND, value);
                case "or":
                    return new Token(TokenType.OR, value);
                default:
                    return new Token(TokenType.IDENTIFIER, value);
            }
        }

        private boolean isHexDigit(char ch) {
            return Character.isDigit(ch) || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
        }

        private byte[] hexStringToByteArray(String s) {
            int len = s.length();
            if (len % 2 != 0) {
                s = "0" + s;
                len++;
            }

            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                        + Character.digit(s.charAt(i + 1), 16));
            }
            return data;
        }

        public List<Token> getTokens() {
            return tokens;
        }
    }

    static class Parser {
        private List<Token> tokens;
        private int pos = 0;

        public Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        public IPeekMatchRule parseMatchRule() {
            MatchRule matchRule = new MatchRule();
            matchRule.rules = parseRules();
            return matchRule;
        }

        private List<Rule> parseRules() {
            List<Rule> rules = new ArrayList<>();
            while (currentToken().type != TokenType.EOF) {
                rules.add(parseRule());
                skipNewlines();
            }
            return rules;
        }

        private void skipNewlines() {
            while (currentToken().type == TokenType.EOF && pos < tokens.size() - 1) {
                advance();
            }
        }

        private Rule parseRule() {
            Rule rule = new Rule();

            if (currentToken().type == TokenType.STAR) {
                rule.isWildcard = true;
                advance();
                rule.condition = new WildcardCondition();
            } else {
                rule.isWildcard = false;
                rule.condition = parseExpression();
            }

            expect(TokenType.ARROW);
            Token resultToken = expect(TokenType.IDENTIFIER);
            rule.resultType = resultToken.value;

            return rule;
        }

        private Expr parseExpression() {
            return parseOr();
        }

        private Expr parseOr() {
            Expr left = parseAnd();
            while (currentToken().type == TokenType.OR) {
                Token op = currentToken();
                advance();
                Expr right = parseAnd();
                LogicalExpr expr = new LogicalExpr();
                expr.left = left;
                expr.right = right;
                expr.operator = op.value;
                left = expr;
            }
            return left;
        }

        private Expr parseAnd() {
            Expr left = parseNot();
            while (currentToken().type == TokenType.AND) {
                Token op = currentToken();
                advance();
                Expr right = parseNot();
                LogicalExpr expr = new LogicalExpr();
                expr.left = left;
                expr.right = right;
                expr.operator = op.value;
                left = expr;
            }
            return left;
        }

        private Expr parseNot() {
            if (currentToken().type == TokenType.NOT) {
                advance();
                NotExpr expr = new NotExpr();
                expr.expr = parsePrimary();
                return expr;
            }
            return parsePrimary();
        }

        private Expr parsePrimary() {
            if (currentToken().type == TokenType.LPAREN) {
                advance();
                Expr expr = parseExpression();
                expect(TokenType.RPAREN);
                return expr;
            } else {
                return parseCondition();
            }
        }

        private Condition parseCondition() {
            Condition condition = new Condition();

            expect(TokenType.GET);
            expect(TokenType.LPAREN);

            Token offsetToken = expect(TokenType.NUMBER);
            condition.offset = Integer.parseInt(offsetToken.value);

            expect(TokenType.COMMA);

            Token lengthToken = expect(TokenType.NUMBER);
            condition.length = Integer.parseInt(lengthToken.value);

            expect(TokenType.RPAREN);

            Token opToken = expect(TokenType.EQUALS);
            condition.operator = opToken.value;

            Token valueToken = currentToken();
            switch (valueToken.type) {
                case STRING:
                    condition.value = valueToken.value;
                    advance();
                    break;
                case HEX:
                    condition.value = valueToken.value;
                    condition.bytes = valueToken.bytes;
                    advance();
                    break;
                case IDENTIFIER:
                    condition.value = valueToken.value;
                    advance();
                    break;
                default:
                    throw new IllegalArgumentException("Expected string, hex, or identifier after " +
                            condition.operator + " but got " + valueToken.type + " (" + valueToken.value + ")");
            }

            return condition;
        }

        private Token currentToken() {
            if (pos >= tokens.size()) {
                return new Token(TokenType.EOF, "");
            }
            return tokens.get(pos);
        }

        private void advance() {
            pos++;
        }

        private Token expect(TokenType expectedType) {
            Token token = currentToken();
            if (token.type != expectedType) {
                throw new IllegalArgumentException("Expected " + expectedType + " but got " + token.type +
                        " (" + token.value + ") at position " + pos);
            }
            advance();
            return token;
        }
    }

    public static IPeekMatchRule parseMatchRule(String input) {
        Lexer lexer = new Lexer(input);
        Parser parser = new Parser(lexer.getTokens());
        return parser.parseMatchRule();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}