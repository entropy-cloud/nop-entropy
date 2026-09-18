package io.nop.jq.jq;

import io.nop.api.core.exceptions.NopException;
import io.nop.jq.NopJqErrors;
import io.nop.jq.NopJqException;

import java.util.List;

/**
 * Parser for jq expressions that translates to XLang expression strings.
 * <p>
 * jq syntax → XLang translation:
 * <ul>
 *   <li>.foo → .foo</li>
 *   <li>.foo.bar → .foo.bar</li>
 *   <li>.[] → .[]</li>
 *   <li>.[0] → .[0]</li>
 *   <li>select(expr) → select(expr)</li>
 *   <li>map(expr) → map(expr)</li>
 *   <li>reduce .[] as $x (0; . + $x) → reduce .[] as x (0; . + x)</li>
 *   <li>expr | expr → expr | expr</li>
 *   <li>{a, b} → {a, b}</li>
 *   <li>[.a, .b] → [.a, .b]</li>
 *   <li>if expr then expr else expr end → if expr then expr else expr end</li>
 *   <li>$var → $var</li>
 * </ul>
 */
public class JqParser {
    private final List<JqToken> tokens;
    private int pos;

    public JqParser(List<JqToken> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    /**
     * Parse the jq expression and return the translated XLang expression string.
     */
    public String parse() {
        String result = parsePipe();
        expect(JqTokenType.EOF);
        return result;
    }

    private String parsePipe() {
        String left = parseExpression();
        while (check(JqTokenType.PIPE)) {
            advance();
            String right = parseExpression();
            left = left + " | " + right;
        }
        return left;
    }

    private String parseExpression() {
        if (check(JqTokenType.IF)) {
            return parseIfElse();
        }
        if (check(JqTokenType.REDUCE)) {
            return parseReduce();
        }
        if (check(JqTokenType.SELECT)) {
            advance();
            expect(JqTokenType.LPAREN);
            String cond = parsePipe();
            expect(JqTokenType.RPAREN);
            return "select(" + cond + ")";
        }
        if (check(JqTokenType.MAP)) {
            advance();
            expect(JqTokenType.LPAREN);
            String expr = parsePipe();
            expect(JqTokenType.RPAREN);
            return "map(" + expr + ")";
        }
        if (check(JqTokenType.LENGTH)) {
            advance();
            return "length";
        }
        if (check(JqTokenType.KEYS)) {
            advance();
            return "keys";
        }
        if (check(JqTokenType.VALUES)) {
            advance();
            return "values";
        }
        if (check(JqTokenType.TYPE)) {
            advance();
            return "type";
        }
        if (check(JqTokenType.RECURSE)) {
            advance();
            if (check(JqTokenType.LPAREN)) {
                advance();
                String inner = parsePipe();
                expect(JqTokenType.RPAREN);
                return "recurse(" + inner + ")";
            }
            return "recurse";
        }
        return parseComparison();
    }

    private String parseComparison() {
        String left = parseAddSub();
        while (check(JqTokenType.EQUAL) || check(JqTokenType.NOT_EQUAL)
                || check(JqTokenType.GREATER) || check(JqTokenType.GREATER_EQ)
                || check(JqTokenType.LESS) || check(JqTokenType.LESS_EQ)
                || check(JqTokenType.AND) || check(JqTokenType.OR)) {
            String op = advance().getText();
            String right = parseAddSub();
            left = left + " " + op + " " + right;
        }
        return left;
    }

    private String parseAddSub() {
        String left = parseMulDiv();
        while (check(JqTokenType.PLUS) || check(JqTokenType.MINUS)) {
            String op = advance().getText();
            String right = parseMulDiv();
            left = left + " " + op + " " + right;
        }
        return left;
    }

    private String parseMulDiv() {
        String left = parseUnary();
        while (check(JqTokenType.MULTIPLY) || check(JqTokenType.DIVIDE) || check(JqTokenType.MODULO)) {
            String op = advance().getText();
            String right = parseUnary();
            left = left + " " + op + " " + right;
        }
        return left;
    }

    private String parseUnary() {
        if (check(JqTokenType.MINUS)) {
            advance();
            String expr = parseUnary();
            return "-" + expr;
        }
        if (check(JqTokenType.NOT)) {
            advance();
            String expr = parseUnary();
            return "not " + expr;
        }
        return parsePostfix();
    }

    private String parsePostfix() {
        String expr = parsePrimary();
        while (true) {
            if (check(JqTokenType.DOT)) {
                advance();
                if (check(JqTokenType.IDENT)) {
                    String prop = advance().getText();
                    expr = expr + "." + prop;
                } else if (check(JqTokenType.LBRACKET)) {
                    advance();
                    String index = parsePipe();
                    expect(JqTokenType.RBRACKET);
                    expr = expr + "[" + index + "]";
                } else if (check(JqTokenType.LBRACE)) {
                    expr = expr + " " + parseObjectConstruct();
                } else {
                    expr = expr + ".";
                }
            } else if (check(JqTokenType.LBRACKET)) {
                advance();
                if (check(JqTokenType.RBRACKET)) {
                    advance();
                    // If expr already ends with a property name (e.g., ".store.book"),
                    // just append "[]", not ".[]"
                    if (!expr.endsWith(".") && !expr.endsWith("[")) {
                        expr = expr + "[]";
                    } else {
                        expr = expr + ".[]";
                    }
                } else if (check(JqTokenType.INTEGER) || check(JqTokenType.MINUS)) {
                    // Could be array index or slice: [0], [2:4], [2:]
                    String first = parsePipe();
                    if (check(JqTokenType.COLON)) {
                        advance(); // skip :
                        if (check(JqTokenType.RBRACKET)) {
                            // Open-ended slice: [2:]
                            advance();
                            expr = expr + "[" + first + ":]";
                        } else if (check(JqTokenType.INTEGER)) {
                            String second = advance().getText();
                            expect(JqTokenType.RBRACKET);
                            expr = expr + "[" + first + ":" + second + "]";
                        } else {
                            expect(JqTokenType.RBRACKET);
                            expr = expr + "[" + first + ":]";
                        }
                    } else {
                        expect(JqTokenType.RBRACKET);
                        expr = expr + "[" + first + "]";
                    }
                } else {
                    String index = parsePipe();
                    expect(JqTokenType.RBRACKET);
                    expr = expr + "[" + index + "]";
                }
            } else if (check(JqTokenType.LPAREN)) {
                // function call - only if preceded by an identifier
                break;
            } else {
                break;
            }
        }
        return expr;
    }

    private String parsePrimary() {
        if (check(JqTokenType.DOT)) {
            advance();
            if (check(JqTokenType.IDENT)) {
                String prop = advance().getText();
                return "." + prop;
            }
            if (check(JqTokenType.LBRACKET)) {
                advance();
                if (check(JqTokenType.RBRACKET)) {
                    advance();
                    return ".[]";
                }
                // Handle array index or slice: [0], [2:4], [2:]
                String first = parsePipe();
                if (check(JqTokenType.COLON)) {
                    advance(); // skip :
                    if (check(JqTokenType.RBRACKET)) {
                        advance();
                        return ".[" + first + ":]";
                    } else if (check(JqTokenType.INTEGER)) {
                        String second = advance().getText();
                        expect(JqTokenType.RBRACKET);
                        return ".[" + first + ":" + second + "]";
                    } else {
                        expect(JqTokenType.RBRACKET);
                        return ".[" + first + ":]";
                    }
                }
                expect(JqTokenType.RBRACKET);
                return ".[" + first + "]";
            }
            if (check(JqTokenType.LBRACE)) {
                return "." + parseObjectConstruct();
            }
            return ".";
        }

        if (check(JqTokenType.DOT_DOT)) {
            advance();
            if (check(JqTokenType.IDENT)) {
                String prop = advance().getText();
                return ".." + prop;
            }
            return "..";
        }

        if (check(JqTokenType.IDENT)) {
            String text = advance().getText();
            // Strip $ prefix for XLang variable references
            return text.startsWith("$") ? text.substring(1) : text;
        }

        if (check(JqTokenType.INTEGER)) {
            return advance().getText();
        }

        if (check(JqTokenType.FLOAT)) {
            return advance().getText();
        }

        if (check(JqTokenType.STRING)) {
            String val = advance().getText();
            return "'" + val + "'";
        }

        if (check(JqTokenType.NULL)) {
            advance();
            return "null";
        }

        if (check(JqTokenType.TRUE)) {
            advance();
            return "true";
        }

        if (check(JqTokenType.FALSE)) {
            advance();
            return "false";
        }

        if (check(JqTokenType.LPAREN)) {
            advance();
            String expr = parsePipe();
            expect(JqTokenType.RPAREN);
            return "(" + expr + ")";
        }

        if (check(JqTokenType.LBRACKET)) {
            return parseArrayConstruct();
        }

        if (check(JqTokenType.LBRACE)) {
            return parseObjectConstruct();
        }

        throw error("Unexpected token: " + peek());
    }

    private String parseIfElse() {
        expect(JqTokenType.IF);
        String cond = parsePipe();
        expect(JqTokenType.THEN);
        String thenExpr = parsePipe();
        StringBuilder sb = new StringBuilder();
        sb.append("if ").append(cond).append(" then ").append(thenExpr);

        while (check(JqTokenType.ELIF)) {
            advance();
            String elifCond = parsePipe();
            expect(JqTokenType.THEN);
            String elifExpr = parsePipe();
            sb.append(" elif ").append(elifCond).append(" then ").append(elifExpr);
        }

        if (check(JqTokenType.ELSE)) {
            advance();
            String elseExpr = parsePipe();
            sb.append(" else ").append(elseExpr);
        }

        expect(JqTokenType.END);
        sb.append(" end");
        return sb.toString();
    }

    private String parseReduce() {
        expect(JqTokenType.REDUCE);
        String expr = parsePipe();
        expect(JqTokenType.AS);
        String varToken = expect(JqTokenType.IDENT).getText();
        // Strip $ prefix for XLang variable name
        String varName = varToken.startsWith("$") ? varToken.substring(1) : varToken;
        expect(JqTokenType.LPAREN);
        String init = parsePipe();
        expect(JqTokenType.SEMICOLON);
        String body = parsePipe();
        expect(JqTokenType.RPAREN);
        return "reduce " + expr + " as " + varName + " (" + init + "; " + body + ")";
    }

    private String parseArrayConstruct() {
        expect(JqTokenType.LBRACKET);
        StringBuilder sb = new StringBuilder("[");
        if (!check(JqTokenType.RBRACKET)) {
            sb.append(parsePipe());
            while (check(JqTokenType.COMMA)) {
                advance();
                sb.append(", ").append(parsePipe());
            }
        }
        expect(JqTokenType.RBRACKET);
        sb.append("]");
        return sb.toString();
    }

    private String parseObjectConstruct() {
        expect(JqTokenType.LBRACE);
        StringBuilder sb = new StringBuilder("{");
        if (!check(JqTokenType.RBRACE)) {
            parseObjectField(sb);
            while (check(JqTokenType.COMMA)) {
                advance();
                sb.append(", ");
                parseObjectField(sb);
            }
        }
        expect(JqTokenType.RBRACE);
        sb.append("}");
        return sb.toString();
    }

    private void parseObjectField(StringBuilder sb) {
        if (check(JqTokenType.IDENT)) {
            String key = advance().getText();
            sb.append(key);
            if (check(JqTokenType.COLON)) {
                advance();
                sb.append(": ").append(parsePipe());
            }
        } else if (check(JqTokenType.STRING)) {
            String key = advance().getText();
            sb.append("'").append(key).append("'");
            expect(JqTokenType.COLON);
            sb.append(": ").append(parsePipe());
        }
    }

    private boolean check(JqTokenType type) {
        return pos < tokens.size() && tokens.get(pos).getType() == type;
    }

    private JqToken advance() {
        return tokens.get(pos++);
    }

    private JqToken peek() {
        return tokens.get(pos);
    }

    private JqToken expect(JqTokenType type) {
        if (!check(type)) {
            throw error("Expected " + type + " but got " + peek());
        }
        return advance();
    }

    private NopException error(String message) {
        return new NopJqException(NopJqErrors.ERR_JQ_COMPILE_ERROR)
                .param("detail", message)
                .param("position", pos < tokens.size() ? tokens.get(pos).getPosition() : -1);
    }
}
