package io.nop.jq.jq;

import io.nop.jq.jq.ast.*;

import java.util.ArrayList;
import java.util.List;

/**
 * New jq parser that produces AST instead of XLang strings.
 * Supports basic jq syntax for direct execution.
 */
public class JqParser {
    private final List<JqToken> tokens;
    private int pos;

    public JqParser(List<JqToken> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    public JqAstNode parse() {
        JqAstNode result = parsePipeWithBind();
        expect(JqTokenType.EOF);
        return result;
    }

    private JqAstNode parsePipe() {
        JqAstNode left = parseComma();
        while (check(JqTokenType.PIPE)) {
            advance();
            JqAstNode right = parseComma();
            left = new PipeNode(left, right);
        }
        return left;
    }

    /**
     * Parse a pipe expression, handling 'as $var | body' binding syntax.
     * This is called from the top level, not from within other expressions.
     */
    private JqAstNode parsePipeWithBind() {
        JqAstNode left = parsePipe();
        if (check(JqTokenType.AS)) {
            advance();
            String varName = expect(JqTokenType.IDENT).getText();
            expect(JqTokenType.PIPE);
            JqAstNode body = parsePipe();
            return new BindNode(left, varName, body);
        }
        return left;
    }

    private JqAstNode parseComma() {
        JqAstNode left = parseExpression();
        while (check(JqTokenType.COMMA)) {
            advance();
            JqAstNode right = parseExpression();
            left = new CommaNode(left, right);
        }
        return left;
    }

    private JqAstNode parseExpression() {
        // if-then-else
        if (check(JqTokenType.IF)) {
            return parseIfThenElse();
        }
        // reduce
        if (check(JqTokenType.REDUCE)) {
            return parseReduce();
        }
        // select
        if (check(JqTokenType.SELECT)) {
            advance();
            expect(JqTokenType.LPAREN);
            JqAstNode cond = parsePipe();
            expect(JqTokenType.RPAREN);
            return new SelectNode(cond, null);
        }
        // map
        if (check(JqTokenType.MAP)) {
            advance();
            expect(JqTokenType.LPAREN);
            JqAstNode func = parsePipe();
            expect(JqTokenType.RPAREN);
            return new MapNode(func, null, false);
        }
        // try
        if (check(JqTokenType.TRY)) {
            advance();
            JqAstNode tryExpr = parsePostfix();
            JqAstNode catchExpr = null;
            if (check(JqTokenType.CATCH)) {
                advance();
                catchExpr = parseExpression();
            }
            return new TryCatchNode(tryExpr, catchExpr);
        }
        // empty
        if (check(JqTokenType.EMPTY)) {
            advance();
            return EmptyNode.INSTANCE;
        }
        // not (jq: negates the input)
        if (check(JqTokenType.NOT)) {
            advance();
            return new BooleanOpNode(BooleanOpNode.Op.NOT, null, IdentityNode.INSTANCE);
        }
        // length, keys, values, type (built-in functions)
        if (check(JqTokenType.LENGTH) || check(JqTokenType.KEYS)
                || check(JqTokenType.VALUES) || check(JqTokenType.TYPE)) {
            JqTokenType type = peek().getType();
            advance();
            return new FuncCallNode(type.name().toLowerCase(), List.of());
        }
        // recurse
        if (check(JqTokenType.RECURSE)) {
            advance();
            JqAstNode arg = null;
            if (check(JqTokenType.LPAREN)) {
                advance();
                arg = parsePipe();
                expect(JqTokenType.RPAREN);
            }
            return new FuncCallNode("recurse", arg != null ? List.of(arg) : List.of());
        }
        // limit
        if (check(JqTokenType.LIMIT)) {
            advance();
            expect(JqTokenType.LPAREN);
            JqAstNode count = parsePipe();
            expect(JqTokenType.SEMICOLON);
            JqAstNode expr = parsePipe();
            expect(JqTokenType.RPAREN);
            return new LimitNode(count, expr);
        }
        return parseComparison();
    }

    private JqAstNode parseLabel() {
        // label
        if (check(JqTokenType.LABEL)) {
            advance();
            String name = expect(JqTokenType.IDENT).getText();
            return new LabelNode(name);
        }
        // break
        if (check(JqTokenType.BREAK)) {
            advance();
            String name = expect(JqTokenType.IDENT).getText();
            return new BreakNode(name);
        }
        return parseComparison();
    }

    private JqAstNode parseComparison() {
        JqAstNode left = parseAddSub();
        while (checkAny(JqTokenType.EQUAL, JqTokenType.NOT_EQUAL,
                JqTokenType.GREATER, JqTokenType.GREATER_EQ,
                JqTokenType.LESS, JqTokenType.LESS_EQ,
                JqTokenType.AND, JqTokenType.OR)) {
            JqToken op = advance();
            JqAstNode right = parseAddSub();
            if (op.getType() == JqTokenType.AND) {
                left = new BooleanOpNode(BooleanOpNode.Op.AND, left, right);
            } else if (op.getType() == JqTokenType.OR) {
                left = new BooleanOpNode(BooleanOpNode.Op.OR, left, right);
            } else {
                ComparisonNode.Op cmpOp;
                switch (op.getType()) {
                    case EQUAL: cmpOp = ComparisonNode.Op.EQ; break;
                    case NOT_EQUAL: cmpOp = ComparisonNode.Op.NE; break;
                    case GREATER: cmpOp = ComparisonNode.Op.GT; break;
                    case GREATER_EQ: cmpOp = ComparisonNode.Op.GE; break;
                    case LESS: cmpOp = ComparisonNode.Op.LT; break;
                    case LESS_EQ: cmpOp = ComparisonNode.Op.LE; break;
                    default: throw new IllegalStateException();
                }
                left = new ComparisonNode(cmpOp, left, right);
            }
        }
        return left;
    }

    private JqAstNode parseAddSub() {
        JqAstNode left = parseMulDiv();
        while (check(JqTokenType.PLUS) || check(JqTokenType.MINUS)) {
            JqToken op = advance();
            JqAstNode right = parseMulDiv();
            MathOpNode.Op mathOp = op.getType() == JqTokenType.PLUS
                    ? MathOpNode.Op.ADD : MathOpNode.Op.SUB;
            left = new MathOpNode(mathOp, left, right);
        }
        return left;
    }

    private JqAstNode parseMulDiv() {
        JqAstNode left = parseUnary();
        while (check(JqTokenType.MULTIPLY) || check(JqTokenType.DIVIDE) || check(JqTokenType.MODULO)) {
            JqToken op = advance();
            JqAstNode right = parseUnary();
            MathOpNode.Op mathOp = op.getType() == JqTokenType.MULTIPLY
                    ? MathOpNode.Op.MUL
                    : (op.getType() == JqTokenType.DIVIDE ? MathOpNode.Op.DIV : MathOpNode.Op.MOD);
            left = new MathOpNode(mathOp, left, right);
        }
        return left;
    }

    private JqAstNode parseUnary() {
        if (check(JqTokenType.MINUS)) {
            advance();
            JqAstNode operand = parsePostfix();
            return new NegateNode(operand);
        }
        return parsePostfix();
    }

    private JqAstNode parsePostfix() {
        JqAstNode node = parsePrimary();
        while (true) {
            if (check(JqTokenType.DOT)) {
                advance();
                if (check(JqTokenType.IDENT)) {
                    String name = advance().getText();
                    node = new FieldAccessNode(name, node);
                } else if (check(JqTokenType.LBRACKET)) {
                    advance();
                    if (check(JqTokenType.RBRACKET)) {
                        advance();
                        node = new IteratorNode(node);
                    } else if (check(JqTokenType.COLON)) {
                        // .[start:end] slice
                        advance();
                        JqAstNode end = null;
                        if (!check(JqTokenType.RBRACKET)) {
                            end = parsePipe();
                        }
                        expect(JqTokenType.RBRACKET);
                        node = new SliceNode(null, end, node);
                    } else {
                        JqAstNode index = parsePipe();
                        if (check(JqTokenType.COLON)) {
                            advance();
                            JqAstNode end = null;
                            if (!check(JqTokenType.RBRACKET)) {
                                end = parsePipe();
                            }
                            expect(JqTokenType.RBRACKET);
                            node = new SliceNode(index, end, node);
                        } else {
                            expect(JqTokenType.RBRACKET);
                            node = new IndexAccessNode(index, node);
                        }
                    }
                } else if (check(JqTokenType.LBRACE)) {
                    node = parseObjectConstructAfterDot(node);
                } else {
                    break;
                }
            } else if (check(JqTokenType.LBRACKET)) {
                advance();
                if (check(JqTokenType.RBRACKET)) {
                    advance();
                    node = new IteratorNode(node);
                } else {
                    JqAstNode index = parsePipe();
                    expect(JqTokenType.RBRACKET);
                    node = new IndexAccessNode(index, node);
                }
            } else if (check(JqTokenType.QUESTION)) {
                // Optional operator: expr? - silently ignore errors
                advance();
                node = new TryCatchNode(node, null);
            } else if (check(JqTokenType.LPAREN)) {
                // Function call
                String name = null;
                if (node instanceof FieldAccessNode fa) {
                    name = fa.fieldName();
                    node = fa.object();
                } else if (node instanceof FuncCallNode fc) {
                    name = fc.name();
                } else if (node instanceof VariableNode vn) {
                    name = vn.name();
                }
                if (name != null) {
                    advance(); // skip (
                    List<JqAstNode> args = new ArrayList<>();
                    if (!check(JqTokenType.RPAREN)) {
                        args.add(parsePipe());
                        while (check(JqTokenType.SEMICOLON)) {
                            advance();
                            args.add(parsePipe());
                        }
                    }
                    expect(JqTokenType.RPAREN);
                    node = new FuncCallNode(name, args);
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        return node;
    }

    private JqAstNode parsePrimary() {
        // def function definition
        if (check(JqTokenType.DEF)) {
            advance();
            String name = expect(JqTokenType.IDENT).getText();
            List<String> params = new ArrayList<>();
            if (check(JqTokenType.LPAREN)) {
                advance();
                if (!check(JqTokenType.RPAREN)) {
                    params.add(expect(JqTokenType.IDENT).getText());
                    while (check(JqTokenType.SEMICOLON)) {
                        advance();
                        params.add(expect(JqTokenType.IDENT).getText());
                    }
                }
                expect(JqTokenType.RPAREN);
            }
            expect(JqTokenType.COLON);
            JqAstNode body = parsePipe();
            expect(JqTokenType.SEMICOLON);
            return new FuncDefNode(name, params, body);
        }
        // @ formatter
        if (check(JqTokenType.AT)) {
            advance();
            String format = expect(JqTokenType.IDENT).getText();
            JqAstNode object = null;
            if (check(JqTokenType.STRING) || check(JqTokenType.DOT) || check(JqTokenType.IDENT)) {
                object = parsePrimary();
            }
            return new FormatNode(format, object);
        }
        // import statement (simplified - just skip)
        if (check(JqTokenType.IMPORT)) {
            advance();
            expect(JqTokenType.STRING);
            expect(JqTokenType.AS);
            expect(JqTokenType.IDENT);
            expect(JqTokenType.SEMICOLON);
            // Return identity for now - imports not fully implemented
            return IdentityNode.INSTANCE;
        }
        // foreach (simplified - treat as reduce for now)
        if (check(JqTokenType.FOREACH)) {
            advance();
            JqAstNode expr = parsePipe();
            expect(JqTokenType.AS);
            String varName = expect(JqTokenType.IDENT).getText();
            expect(JqTokenType.LPAREN);
            JqAstNode init = parsePipe();
            expect(JqTokenType.SEMICOLON);
            JqAstNode body = parsePipe();
            expect(JqTokenType.RPAREN);
            // Simplified: treat foreach as reduce
            return new ReduceNode(expr, varName, init, body);
        }
        // error function
        if (check(JqTokenType.ERROR)) {
            advance();
            if (check(JqTokenType.LPAREN)) {
                advance();
                JqAstNode msg = parsePipe();
                expect(JqTokenType.RPAREN);
                return new ErrorNode(msg);
            }
            return new ErrorNode(null);
        }
        // while loop (simplified - treat as reduce)
        if (check(JqTokenType.WHILE)) {
            advance();
            JqAstNode condition = parsePipe();
            expect(JqTokenType.LPAREN);
            JqAstNode body = parsePipe();
            expect(JqTokenType.RPAREN);
            // Simplified: while is not fully supported, return identity
            return IdentityNode.INSTANCE;
        }
        // until loop (simplified - treat as reduce)
        if (check(JqTokenType.UNTIL)) {
            advance();
            JqAstNode condition = parsePipe();
            expect(JqTokenType.LPAREN);
            JqAstNode body = parsePipe();
            expect(JqTokenType.RPAREN);
            // Simplified: until is not fully supported, return identity
            return IdentityNode.INSTANCE;
        }
        if (check(JqTokenType.DOT)) {
            advance();
            if (check(JqTokenType.IDENT)) {
                String name = advance().getText();
                return new FieldAccessNode(name, null);
            }
            if (check(JqTokenType.DOT_DOT)) {
                advance();
                if (check(JqTokenType.IDENT)) {
                    String name = advance().getText();
                    return new RecursiveDescentNode(null, name);
                }
                return new RecursiveDescentNode(null, null);
            }
            if (check(JqTokenType.LBRACKET)) {
                advance();
                if (check(JqTokenType.RBRACKET)) {
                    advance();
                    return new IteratorNode(null);
                }
                JqAstNode index = parsePipe();
                if (check(JqTokenType.COLON)) {
                    advance();
                    JqAstNode end = null;
                    if (!check(JqTokenType.RBRACKET)) {
                        end = parsePipe();
                    }
                    expect(JqTokenType.RBRACKET);
                    return new SliceNode(index, end, null);
                }
                expect(JqTokenType.RBRACKET);
                return new IndexAccessNode(index, null);
            }
            return IdentityNode.INSTANCE;
        }
        if (check(JqTokenType.DOT_DOT)) {
            advance();
            if (check(JqTokenType.IDENT)) {
                String name = advance().getText();
                return new RecursiveDescentNode(null, name);
            }
            return new RecursiveDescentNode(null, null);
        }
        if (check(JqTokenType.NULL)) {
            advance();
            return NullLiteralNode.INSTANCE;
        }
        if (check(JqTokenType.TRUE)) {
            advance();
            return new BooleanLiteralNode(true);
        }
        if (check(JqTokenType.FALSE)) {
            advance();
            return new BooleanLiteralNode(false);
        }
        if (check(JqTokenType.INTEGER)) {
            JqToken tok = advance();
            return new NumberLiteralNode(Integer.parseInt(tok.getText()));
        }
        if (check(JqTokenType.FLOAT)) {
            JqToken tok = advance();
            return new NumberLiteralNode(Double.parseDouble(tok.getText()));
        }
        if (check(JqTokenType.STRING)) {
            JqToken tok = advance();
            String text = tok.getText();
            // Check for string interpolation: \(...)
            if (text.contains("\\(")) {
                return parseStringInterpolation(text);
            }
            return new StringLiteralNode(text);
        }
        if (check(JqTokenType.IDENT)) {
            String name = advance().getText();
            return new VariableNode(name);
        }
        if (check(JqTokenType.LPAREN)) {
            advance();
            JqAstNode expr = parsePipe();
            expect(JqTokenType.RPAREN);
            return expr;
        }
        if (check(JqTokenType.LBRACKET)) {
            return parseArrayConstruct();
        }
        if (check(JqTokenType.LBRACE)) {
            return parseObjectConstruct();
        }
        throw error("Unexpected token: " + peek());
    }

    private JqAstNode parseIfThenElse() {
        expect(JqTokenType.IF);
        JqAstNode cond = parsePipe();
        expect(JqTokenType.THEN);
        JqAstNode thenBranch = parsePipe();
        List<IfThenElseNode.ElifClause> elifClauses = new ArrayList<>();
        while (check(JqTokenType.ELIF)) {
            advance();
            JqAstNode elifCond = parsePipe();
            expect(JqTokenType.THEN);
            JqAstNode elifBranch = parsePipe();
            elifClauses.add(new IfThenElseNode.ElifClause(elifCond, elifBranch));
        }
        JqAstNode elseBranch = null;
        if (check(JqTokenType.ELSE)) {
            advance();
            elseBranch = parsePipe();
        }
        expect(JqTokenType.END);
        return new IfThenElseNode(cond, thenBranch, elifClauses, elseBranch);
    }

    private JqAstNode parseReduce() {
        expect(JqTokenType.REDUCE);
        JqAstNode expr = parsePipe();
        expect(JqTokenType.AS);
        String varName = expect(JqTokenType.IDENT).getText();
        expect(JqTokenType.LPAREN);
        JqAstNode init = parsePipe();
        expect(JqTokenType.SEMICOLON);
        JqAstNode body = parsePipe();
        expect(JqTokenType.RPAREN);
        return new ReduceNode(expr, varName, init, body);
    }

    private JqAstNode parseArrayConstruct() {
        expect(JqTokenType.LBRACKET);
        if (check(JqTokenType.RBRACKET)) {
            advance();
            return new ArrayConstructNode(EmptyNode.INSTANCE);
        }
        JqAstNode element = parsePipe();
        expect(JqTokenType.RBRACKET);
        return new ArrayConstructNode(element);
    }

    private JqAstNode parseObjectConstruct() {
        return parseObjectConstructAfterDot(null);
    }

    private JqAstNode parseObjectConstructAfterDot(JqAstNode object) {
        expect(JqTokenType.LBRACE);
        List<ObjectConstructNode.Field> fields = new ArrayList<>();
        if (!check(JqTokenType.RBRACE)) {
            parseObjectField(fields);
            while (check(JqTokenType.COMMA)) {
                advance();
                parseObjectField(fields);
            }
        }
        expect(JqTokenType.RBRACE);
        if (object != null) {
            return new PipeNode(object, new ObjectConstructNode(fields));
        }
        return new ObjectConstructNode(fields);
    }

    private void parseObjectField(List<ObjectConstructNode.Field> fields) {
        if (check(JqTokenType.IDENT)) {
            String key = advance().getText();
            if (check(JqTokenType.COLON)) {
                advance();
                JqAstNode value = parseExpression();
                fields.add(new ObjectConstructNode.Field(new StringLiteralNode(key), value));
            } else {
                // shorthand: {foo} means {foo: .foo}
                fields.add(new ObjectConstructNode.Field(
                        new StringLiteralNode(key),
                        new FieldAccessNode(key, null)));
            }
        } else if (check(JqTokenType.STRING)) {
            String key = advance().getText();
            expect(JqTokenType.COLON);
            JqAstNode value = parseExpression();
            fields.add(new ObjectConstructNode.Field(new StringLiteralNode(key), value));
        } else if (check(JqTokenType.LPAREN)) {
            advance();
            JqAstNode key = parsePipe();
            expect(JqTokenType.RPAREN);
            expect(JqTokenType.COLON);
            JqAstNode value = parseExpression();
            fields.add(new ObjectConstructNode.Field(key, value));
        }
    }

    // Helper methods

    private JqAstNode parseStringInterpolation(String text) {
        // Parse string with \(...) interpolation
        List<Object> parts = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int interpStart = text.indexOf("\\(", i);
            if (interpStart == -1) {
                // No more interpolation, add remaining text
                if (i < text.length()) {
                    parts.add(text.substring(i));
                }
                break;
            }
            // Add text before interpolation
            if (interpStart > i) {
                parts.add(text.substring(i, interpStart));
            }
            // Find matching closing paren
            int parenStart = interpStart + 2; // skip \(
            int depth = 1;
            int j = parenStart;
            while (j < text.length() && depth > 0) {
                if (text.charAt(j) == '(') depth++;
                else if (text.charAt(j) == ')') depth--;
                j++;
            }
            if (depth != 0) {
                throw error("Unterminated string interpolation");
            }
            // Parse the interpolated expression
            String interpExpr = text.substring(parenStart, j - 1);
            // Create a temporary parser for the expression
            JqLexer interpLexer = new JqLexer(interpExpr);
            List<JqToken> interpTokens = interpLexer.tokenize();
            JqParser interpParser = new JqParser(interpTokens);
            JqAstNode interpAst = interpParser.parse();
            parts.add(interpAst);
            i = j;
        }
        if (parts.size() == 1 && parts.get(0) instanceof String) {
            return new StringLiteralNode((String) parts.get(0));
        }
        return new StringInterpNode(parts);
    }

    private boolean check(JqTokenType type) {
        return pos < tokens.size() && tokens.get(pos).getType() == type;
    }

    private boolean checkAny(JqTokenType... types) {
        if (pos >= tokens.size()) return false;
        JqTokenType current = tokens.get(pos).getType();
        for (JqTokenType t : types) {
            if (current == t) return true;
        }
        return false;
    }

    private JqToken advance() {
        return tokens.get(pos++);
    }

    private JqToken peek() {
        return pos < tokens.size() ? tokens.get(pos) : tokens.get(tokens.size() - 1);
    }

    private JqToken expect(JqTokenType type) {
        if (!check(type)) {
            throw error("Expected " + type + " but got " + peek().getType());
        }
        return advance();
    }

    private RuntimeException error(String message) {
        return new RuntimeException("Parse error: " + message + " at position " + pos);
    }
}
