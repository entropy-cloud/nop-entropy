package io.nop.jq;

import io.nop.jq.ast.*;
import io.nop.jq.runtime.JqRuntimeException;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser producing the jq AST.
 *
 * <p>Precedence (low to high) follows the jq grammar:
 * {@code | } then {@code ,} then {@code //} then assignment
 * ({@code = |= += -= *= /= %= //=}) then {@code or}, {@code and},
 * comparisons, {@code + -}, {@code * / %}, unary minus, postfix.
 *
 * <p>{@code Term as PATTERN | rest} binds at term level: the body extends over
 * the rest of the enclosing pipe expression.
 */
public class JqParser {
    private final List<JqToken> tokens;
    private int pos;

    public JqParser(List<JqToken> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    public JqAstNode parse() {
        JqAstNode result = parsePipe();
        expect(JqTokenType.EOF);
        return result;
    }

    private JqAstNode parsePipe() {
        // def f: BODY; REST — the definition scopes over the rest of the pipe
        if (check(JqTokenType.DEF)) {
            FuncDefNode def = parseFuncDef();
            JqAstNode rest = parsePipe();
            return new PipeNode(def, rest);
        }
        // label $x | REST — break $x unwinds to here
        if (check(JqTokenType.LABEL)) {
            advance();
            String name = expect(JqTokenType.IDENT).getText();
            expect(JqTokenType.PIPE);
            JqAstNode body = parsePipe();
            return new LabelNode(name, body);
        }

        JqAstNode left = parseComma();
        while (check(JqTokenType.PIPE)) {
            advance();
            JqAstNode right = parseComma();
            left = new PipeNode(left, right);
        }
        return left;
    }

    private FuncDefNode parseFuncDef() {
        expect(JqTokenType.DEF);
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

    private JqAstNode parseComma() {
        JqAstNode left = parseAlternative();
        while (check(JqTokenType.COMMA)) {
            advance();
            if (check(JqTokenType.DEF)) {
                // `a, def f: b; rest` — the definition scopes over the rest
                FuncDefNode def = parseFuncDef();
                JqAstNode rest = parsePipe();
                left = new CommaNode(left, new PipeNode(def, rest));
                return left;
            }
            JqAstNode right = parseAlternative();
            left = new CommaNode(left, right);
        }
        return left;
    }

    private JqAstNode parseAlternative() {
        JqAstNode left = parseAssign();
        if (check(JqTokenType.ALTERNATIVE)) {
            advance();
            JqAstNode right = parseAlternative(); // right-associative
            return new AlternativeNode(left, right);
        }
        return left;
    }

    private JqAstNode parseAssign() {
        JqAstNode left = parseOr();
        JqTokenType type = peek().getType();
        UpdateAssignNode.Op op = switch (type) {
            case ASSIGN -> UpdateAssignNode.Op.ASSIGN;
            case PIPE_ASSIGN -> UpdateAssignNode.Op.UPDATE;
            case PLUS_ASSIGN -> UpdateAssignNode.Op.ADD;
            case MINUS_ASSIGN -> UpdateAssignNode.Op.SUB;
            case MULTIPLY_ASSIGN -> UpdateAssignNode.Op.MUL;
            case DIVIDE_ASSIGN -> UpdateAssignNode.Op.DIV;
            case MODULO_ASSIGN -> UpdateAssignNode.Op.MOD;
            case ALTERNATIVE_ASSIGN -> UpdateAssignNode.Op.ALTERNATIVE;
            default -> null;
        };
        if (op != null) {
            advance();
            JqAstNode right = parseAlternative();
            return new UpdateAssignNode(op, left, right);
        }
        return left;
    }

    private JqAstNode parseOr() {
        JqAstNode left = parseAnd();
        while (check(JqTokenType.OR)) {
            advance();
            JqAstNode right = parseAnd();
            left = new BooleanOpNode(BooleanOpNode.Op.OR, left, right);
        }
        return left;
    }

    private JqAstNode parseAnd() {
        JqAstNode left = parseComparison();
        while (check(JqTokenType.AND)) {
            advance();
            JqAstNode right = parseComparison();
            left = new BooleanOpNode(BooleanOpNode.Op.AND, left, right);
        }
        return left;
    }

    private JqAstNode parseComparison() {
        JqAstNode left = parseAddSub();
        if (checkAny(JqTokenType.EQUAL, JqTokenType.NOT_EQUAL,
                JqTokenType.GREATER, JqTokenType.GREATER_EQ,
                JqTokenType.LESS, JqTokenType.LESS_EQ)) {
            JqToken op = advance();
            JqAstNode right = parseAddSub();
            ComparisonNode.Op cmpOp = switch (op.getType()) {
                case EQUAL -> ComparisonNode.Op.EQ;
                case NOT_EQUAL -> ComparisonNode.Op.NE;
                case GREATER -> ComparisonNode.Op.GT;
                case GREATER_EQ -> ComparisonNode.Op.GE;
                case LESS -> ComparisonNode.Op.LT;
                case LESS_EQ -> ComparisonNode.Op.LE;
                default -> throw error("Unknown comparison operator " + op);
            };
            return new ComparisonNode(cmpOp, left, right);
        }
        return left;
    }

    private JqAstNode parseAddSub() {
        JqAstNode left = parseMulDiv();
        while (check(JqTokenType.PLUS) || check(JqTokenType.MINUS)) {
            JqToken op = advance();
            JqAstNode right = parseMulDiv();
            left = new MathOpNode(op.getType() == JqTokenType.PLUS
                    ? MathOpNode.Op.ADD : MathOpNode.Op.SUB, left, right);
        }
        return left;
    }

    private JqAstNode parseMulDiv() {
        JqAstNode left = parseUnary();
        while (checkAny(JqTokenType.MULTIPLY, JqTokenType.DIVIDE, JqTokenType.MODULO)) {
            JqToken op = advance();
            JqAstNode right = parseUnary();
            MathOpNode.Op mathOp = switch (op.getType()) {
                case MULTIPLY -> MathOpNode.Op.MUL;
                case DIVIDE -> MathOpNode.Op.DIV;
                default -> MathOpNode.Op.MOD;
            };
            left = new MathOpNode(mathOp, left, right);
        }
        return left;
    }

    private JqAstNode parseUnary() {
        if (check(JqTokenType.MINUS)) {
            advance();
            return new NegateNode(parsePostfix(false));
        }
        return parsePostfix(true);
    }

    private JqAstNode parsePostfix(boolean allowAs) {
        JqAstNode node = parsePrimary();
        node = parsePostfixOps(node);
        if (allowAs && check(JqTokenType.AS)) {
            advance();
            BindPattern pattern = parsePattern();
            expect(JqTokenType.PIPE);
            JqAstNode body = parsePipe();
            return new BindNode(node, pattern, body);
        }
        return node;
    }

    private JqAstNode parsePostfixOps(JqAstNode node) {
        while (true) {
            if (check(JqTokenType.QUESTION)) {
                advance();
                node = new TryCatchNode(node, null);
            } else if (check(JqTokenType.DOT)) {
                advance();
                if (check(JqTokenType.STRING)) {
                    JqToken key = advance();
                    node = fieldAccess(node, key);
                } else if (check(JqTokenType.IDENT) || isFieldKeyword(peek().getType())) {
                    String name = advance().getText();
                    node = new FieldAccessNode(name, node);
                } else if (check(JqTokenType.LBRACKET)) {
                    node = parseBracketSuffix(node);
                } else {
                    break;
                }
            } else if (check(JqTokenType.LBRACKET)) {
                node = parseBracketSuffix(node);
            } else if (check(JqTokenType.LPAREN)) {
                JqAstNode call = parseCallArgs(node);
                if (call == null)
                    break;
                node = call;
            } else {
                break;
            }
        }
        return node;
    }

    /** Parse an index/slice/iterate suffix starting at '['. */
    private JqAstNode parseBracketSuffix(JqAstNode node) {
        expect(JqTokenType.LBRACKET);
        if (check(JqTokenType.RBRACKET)) {
            advance();
            return new IteratorNode(node);
        }
        if (check(JqTokenType.COLON)) {
            advance();
            JqAstNode end = check(JqTokenType.RBRACKET) ? null : parsePipe();
            expect(JqTokenType.RBRACKET);
            return new SliceNode(null, end, node);
        }
        JqAstNode index = parsePipe();
        if (check(JqTokenType.COLON)) {
            advance();
            JqAstNode end = check(JqTokenType.RBRACKET) ? null : parsePipe();
            expect(JqTokenType.RBRACKET);
            return new SliceNode(index, end, node);
        }
        expect(JqTokenType.RBRACKET);
        return new IndexAccessNode(index, node);
    }

    /**
     * Parse a function-call argument list '(' (only when node names a function
     * or variable). Returns null when the '(' is grouping and must be handled
     * elsewhere.
     */
    private JqAstNode parseCallArgs(JqAstNode node) {
        String name = null;
        if (node instanceof FuncCallNode fc && fc.args().isEmpty()) {
            name = fc.name();
        } else if (node instanceof VariableNode vn && vn.name().startsWith("$")) {
            // user function referenced as $name is not jq syntax; treat '(' as grouping
            return null;
        } else if (node instanceof FieldAccessNode fa && fa.object() == null) {
            name = fa.fieldName();
        }
        if (name == null)
            return null;
        advance(); // consume '('
        List<JqAstNode> args = new ArrayList<>();
        if (!check(JqTokenType.RPAREN)) {
            args.add(parsePipe());
            while (check(JqTokenType.SEMICOLON)) {
                advance();
                args.add(parsePipe());
            }
        }
        expect(JqTokenType.RPAREN);
        return new FuncCallNode(name, args);
    }

    private JqAstNode parsePrimary() {
        switch (peek().getType()) {
            case DOT -> {
                return parseDotPrimary();
            }
            case DOT_DOT -> {
                advance();
                if (check(JqTokenType.IDENT)) {
                    return new RecursiveDescentNode(null, advance().getText());
                }
                return new RecursiveDescentNode(null, null);
            }
            case NULL -> {
                advance();
                return NullLiteralNode.INSTANCE;
            }
            case TRUE -> {
                advance();
                return new BooleanLiteralNode(true);
            }
            case FALSE -> {
                advance();
                return new BooleanLiteralNode(false);
            }
            case INTEGER -> {
                return new NumberLiteralNode(parseIntegerLiteral(advance().getText()));
            }
            case FLOAT -> {
                return new NumberLiteralNode(Double.parseDouble(advance().getText()));
            }
            case STRING -> {
                return parseStringLiteral(advance());
            }
            case LPAREN -> {
                advance();
                JqAstNode expr = parsePipe();
                expect(JqTokenType.RPAREN);
                return expr;
            }
            case LBRACKET -> {
                return parseArrayConstruct();
            }
            case LBRACE -> {
                return parseObjectConstruct();
            }
            case AT -> {
                return parseFormat();
            }
            case IDENT -> {
                String name = advance().getText();
                if (name.startsWith("$")) {
                    return new VariableNode(name);
                }
                return new FuncCallNode(name, List.of());
            }
            case IF -> {
                return parseIfThenElse();
            }
            case TRY -> {
                advance();
                // jq grammar: "try" Exp "catch" Exp with Exp at alternative level —
                // commas and pipes after the handler apply outside the try
                JqAstNode tryExpr = parseAlternative();
                JqAstNode catchExpr = null;
                if (check(JqTokenType.CATCH)) {
                    advance();
                    catchExpr = parseAlternative();
                }
                return new TryCatchNode(tryExpr, catchExpr);
            }
            case REDUCE -> {
                return parseReduce();
            }
            case FOREACH -> {
                return parseForeach();
            }
            case WHILE -> {
                advance();
                expect(JqTokenType.LPAREN);
                JqAstNode condition = parsePipe();
                expect(JqTokenType.SEMICOLON);
                JqAstNode update = parsePipe();
                expect(JqTokenType.RPAREN);
                return new WhileNode(condition, update);
            }
            case UNTIL -> {
                advance();
                expect(JqTokenType.LPAREN);
                JqAstNode condition = parsePipe();
                expect(JqTokenType.SEMICOLON);
                JqAstNode update = parsePipe();
                expect(JqTokenType.RPAREN);
                return new UntilNode(condition, update);
            }
            case LIMIT -> {
                advance();
                expect(JqTokenType.LPAREN);
                JqAstNode count = parsePipe();
                expect(JqTokenType.SEMICOLON);
                JqAstNode expr = parsePipe();
                expect(JqTokenType.RPAREN);
                return new FuncCallNode("limit", List.of(count, expr));
            }
            case RECURSE -> {
                advance();
                if (check(JqTokenType.LPAREN)) {
                    advance();
                    JqAstNode arg = parsePipe();
                    expect(JqTokenType.RPAREN);
                    return new FuncCallNode("recurse", List.of(arg));
                }
                return new FuncCallNode("recurse", List.of());
            }
            case SELECT, MAP -> {
                String name = advance().getText();
                expect(JqTokenType.LPAREN);
                JqAstNode arg = parsePipe();
                expect(JqTokenType.RPAREN);
                return new FuncCallNode(name, List.of(arg));
            }
            case EMPTY -> {
                advance();
                return EmptyNode.INSTANCE;
            }
            case NOT -> {
                advance();
                return new FuncCallNode("not", List.of());
            }
            case LENGTH, KEYS, VALUES, TYPE -> {
                String name = advance().getText();
                return new FuncCallNode(name, List.of());
            }
            case INPUT -> {
                advance();
                return new InputNode(false);
            }
            case INPUTS -> {
                advance();
                return new InputNode(true);
            }
            case DEBUG -> {
                advance();
                return new FuncCallNode("debug", List.of());
            }
            case ENV -> {
                advance();
                return EnvNode.INSTANCE;
            }
            case BREAK -> {
                advance();
                String name = expect(JqTokenType.IDENT).getText();
                return new BreakNode(name);
            }
            case ERROR -> {
                advance();
                if (check(JqTokenType.LPAREN)) {
                    advance();
                    JqAstNode msg = parsePipe();
                    expect(JqTokenType.RPAREN);
                    return new ErrorNode(msg);
                }
                return new ErrorNode(null);
            }
            default -> {
                // module / import are intentionally unsupported
                if (check(JqTokenType.MODULE) || check(JqTokenType.IMPORT)) {
                    throw error("jq modules are not supported: " + peek().getText());
                }
                throw error("Unexpected token: " + peek().getText());
            }
        }
    }

    private JqAstNode parseDotPrimary() {
        advance(); // consume '.'
        if (check(JqTokenType.STRING)) {
            JqToken key = advance();
            return fieldAccess(null, key);
        }
        if (check(JqTokenType.IDENT) || isFieldKeyword(peek().getType())) {
            String name = advance().getText();
            return new FieldAccessNode(name, null);
        }
        if (check(JqTokenType.DOT_DOT)) {
            advance();
            if (check(JqTokenType.IDENT)) {
                return new RecursiveDescentNode(null, advance().getText());
            }
            return new RecursiveDescentNode(null, null);
        }
        if (check(JqTokenType.LBRACKET)) {
            return parseBracketSuffix(null);
        }
        return IdentityNode.INSTANCE;
    }

    /** Build a field access from a `.key` / `."key"` suffix (string may interpolate). */
    private JqAstNode fieldAccess(JqAstNode object, JqToken keyToken) {
        JqAstNode key = parseStringLiteral(keyToken);
        if (key instanceof StringLiteralNode s) {
            return new FieldAccessNode(s.value(), object);
        }
        // dynamic key: treat as index access
        return new IndexAccessNode(key, object);
    }

    private JqAstNode parseStringLiteral(JqToken token) {
        String raw = token.getText();
        if (!raw.contains("\\(")) {
            String text = decodeStringEscapes(raw, token.getPosition());
            if (text == null)
                return new StringLiteralNode("");
            return new StringLiteralNode(text);
        }
        return parseStringInterpolation(raw, token.getPosition());
    }

    /**
     * Split a raw string body on \(...) interpolation. Literal parts have their
     * escapes decoded; interpolation expressions are parsed as sub-programs.
     */
    private JqAstNode parseStringInterpolation(String raw, int basePos) {
        List<Object> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '(') {
                // find matching close paren, honouring nested strings/parens
                int depth = 1;
                int j = i + 2;
                while (j < raw.length() && depth > 0) {
                    char cj = raw.charAt(j);
                    if (cj == '\'' || cj == '"') {
                        j = skipRawString(raw, j, cj);
                        continue;
                    }
                    if (cj == '(') depth++;
                    else if (cj == ')') depth--;
                    else if (cj == '\\' && j + 1 < raw.length()) j++;
                    j++;
                }
                if (depth != 0)
                    throw error("Unterminated string interpolation");
                String exprText = raw.substring(i + 2, j - 1);
                if (literal.length() > 0) {
                    parts.add(literal.toString());
                    literal.setLength(0);
                }
                parts.add(parseSubExpression(exprText, basePos + i));
                i = j;
                continue;
            }
            if (c == '\\' && i + 1 < raw.length()) {
                i = decodeEscape(raw, i, literal, basePos);
                continue;
            }
            literal.append(c);
            i++;
        }
        if (literal.length() > 0 || parts.isEmpty())
            parts.add(literal.toString());
        if (parts.size() == 1 && parts.get(0) instanceof String s)
            return new StringLiteralNode(s);
        return new StringInterpNode(parts);
    }

    /** Index just past the closing quote of a raw string starting at start. */
    private int skipRawString(String raw, int start, char quote) {
        int i = start + 1;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                i += 2;
                continue;
            }
            if (c == quote)
                return i + 1;
            i++;
        }
        return i;
    }

    private JqAstNode parseSubExpression(String text, int position) {
        try {
            List<JqToken> subTokens = new JqLexer(text).tokenize();
            JqParser subParser = new JqParser(subTokens);
            return subParser.parse();
        } catch (JqRuntimeException e) {
            throw error("Invalid interpolation expression '" + text + "': " + e.getMessage());
        }
    }

    /**
     * Decode escape sequences in a raw string body. Interpolation sequences are
     * rejected here (the caller handles them before decoding literal parts).
     */
    private String decodeStringEscapes(String raw, int basePos) {
        if (raw.indexOf('\\') < 0)
            return raw;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '(') {
                throw error("Interpolation not allowed in this string");
            }
            if (c == '\\' && i + 1 < raw.length()) {
                i = decodeEscape(raw, i, sb, basePos);
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** Decode one escape at raw[i] == '\\', appending to sb; returns index after the escape. */
    private int decodeEscape(String raw, int i, StringBuilder sb, int basePos) {
        char next = raw.charAt(i + 1);
        switch (next) {
            case 'n' -> sb.append('\n');
            case 't' -> sb.append('\t');
            case 'r' -> sb.append('\r');
            case 'b' -> sb.append('\b');
            case 'f' -> sb.append('\f');
            case '/' -> sb.append('/');
            case '\\' -> sb.append('\\');
            case '"' -> sb.append('"');
            case '\'' -> sb.append('\'');
            case 'u' -> {
                if (i + 6 > raw.length() || !isHexQuad(raw, i + 2)) {
                    throw error("Invalid \\u escape in string");
                }
                int code = Integer.parseInt(raw.substring(i + 2, i + 6), 16);
                i += 6;
                if (Character.isHighSurrogate((char) code)
                        && i + 6 <= raw.length() && raw.charAt(i) == '\\' && raw.charAt(i + 1) == 'u'
                        && isHexQuad(raw, i + 2)) {
                    int low = Integer.parseInt(raw.substring(i + 2, i + 6), 16);
                    if (Character.isLowSurrogate((char) low)) {
                        sb.appendCodePoint(Character.toCodePoint((char) code, (char) low));
                        return i + 6;
                    }
                }
                sb.append((char) code);
                return i;
            }
            case '(' -> throw error("Interpolation not allowed in this string");
            default -> throw error("Invalid escape \\" + next + " in string");
        }
        return i + 2;
    }

    private boolean isHexQuad(String raw, int start) {
        if (start + 4 > raw.length())
            return false;
        for (int k = start; k < start + 4; k++) {
            char c = raw.charAt(k);
            if (!Character.isDigit(c) && (c < 'a' || c > 'f') && (c < 'A' || c > 'F'))
                return false;
        }
        return true;
    }

    private Number parseIntegerLiteral(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e2) {
                return Double.parseDouble(text);
            }
        }
    }

    private JqAstNode parseFormat() {
        expect(JqTokenType.AT);
        String format = expect(JqTokenType.IDENT).getText();
        JqAstNode object = null;
        if (check(JqTokenType.STRING)) {
            object = parseStringLiteral(advance());
        } else if (check(JqTokenType.DOT) || check(JqTokenType.IDENT)) {
            object = parsePostfix(true);
        }
        return new FormatNode(format, object);
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
        JqAstNode source = parsePostfix(false);
        expect(JqTokenType.AS);
        BindPattern pattern = parsePattern();
        expect(JqTokenType.LPAREN);
        JqAstNode init = parsePipe();
        expect(JqTokenType.SEMICOLON);
        JqAstNode update = parsePipe();
        expect(JqTokenType.RPAREN);
        return new ReduceNode(source, pattern, init, update);
    }

    private JqAstNode parseForeach() {
        expect(JqTokenType.FOREACH);
        JqAstNode source = parsePostfix(false);
        expect(JqTokenType.AS);
        BindPattern pattern = parsePattern();
        expect(JqTokenType.LPAREN);
        JqAstNode init = parsePipe();
        expect(JqTokenType.SEMICOLON);
        JqAstNode update = parsePipe();
        JqAstNode extract = null;
        if (check(JqTokenType.SEMICOLON)) {
            advance();
            extract = parsePipe();
        }
        expect(JqTokenType.RPAREN);
        return new ForEachNode(source, pattern, init, update, extract);
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
        return new ObjectConstructNode(fields);
    }

    private void parseObjectField(List<ObjectConstructNode.Field> fields) {
        JqTokenType type = peek().getType();
        // keyword keys: {if: 1}, {and: 2}, ...
        if (isKeyword(type) && type != JqTokenType.NULL && type != JqTokenType.TRUE
                && type != JqTokenType.FALSE) {
            JqToken key = advance();
            objectFieldTail(fields, new StringLiteralNode(key.getText()));
            return;
        }
        switch (type) {
            case IDENT -> {
                JqToken key = advance();
                if (key.getText().startsWith("$")) {
                    if (key.getText().equals("$__loc__")) {
                        // {$__loc__} => {"__loc__": <location>}
                        fields.add(new ObjectConstructNode.Field(
                                new StringLiteralNode("__loc__"),
                                new VariableNode("$__loc__")));
                    } else if (check(JqTokenType.COLON)) {
                        // {$y: 4} => {($y): 4} — the key is the value of $y
                        advance();
                        fields.add(new ObjectConstructNode.Field(
                                new VariableNode(key.getText()), parseObjectValue()));
                    } else {
                        fields.add(shorthandField(key.getText().substring(1),
                                new VariableNode(key.getText())));
                    }
                } else {
                    objectFieldTail(fields, new StringLiteralNode(key.getText()));
                }
            }
            case STRING -> {
                JqAstNode key = parseStringLiteral(advance());
                if (key instanceof StringLiteralNode s) {
                    objectFieldTail(fields, key);
                } else {
                    // dynamic key: {(expr): value} requires parens, but an
                    // interpolated string key without ':' is the shorthand form
                    if (check(JqTokenType.COLON)) {
                        advance();
                        fields.add(new ObjectConstructNode.Field(key, parseObjectValue()));
                    } else {
                        fields.add(new ObjectConstructNode.Field(key,
                                new IndexAccessNode(key, null)));
                    }
                }
            }
            case LPAREN -> {
                advance();
                JqAstNode key = parsePipe();
                expect(JqTokenType.RPAREN);
                expect(JqTokenType.COLON);
                fields.add(new ObjectConstructNode.Field(key, parseObjectValue()));
            }
            case NULL -> {
                advance();
                objectFieldTail(fields, NullLiteralNode.INSTANCE);
            }
            case TRUE -> {
                advance();
                objectFieldTail(fields, new BooleanLiteralNode(true));
            }
            case FALSE -> {
                advance();
                objectFieldTail(fields, new BooleanLiteralNode(false));
            }
            default -> throw error("Unexpected token in object construction: " + peek().getText());
        }
    }

    /** Handle the ': value' or shorthand form after a static key node. */
    private void objectFieldTail(List<ObjectConstructNode.Field> fields, JqAstNode keyNode) {
        if (check(JqTokenType.COLON)) {
            advance();
            fields.add(new ObjectConstructNode.Field(keyNode, parseObjectValue()));
        } else {
            String key = ((StringLiteralNode) keyNode).value();
            fields.add(shorthandField(key, new FieldAccessNode(key, null)));
        }
    }

    private ObjectConstructNode.Field shorthandField(String key, JqAstNode value) {
        return new ObjectConstructNode.Field(new StringLiteralNode(key), value);
    }

    /** Object values bind at alternative level: they must not swallow commas. */
    private JqAstNode parseObjectValue() {
        return parseAlternative();
    }

    // ---- destructuring patterns ----

    private BindPattern parsePattern() {
        List<BindPattern.Alt.Alternative> alternatives = new ArrayList<>();
        alternatives.add(parseSinglePattern());
        while (check(JqTokenType.QUESTION_SLASH)) {
            advance();
            alternatives.add(parseSinglePattern());
        }
        if (alternatives.size() == 1) {
            return alternatives.get(0).pattern();
        }
        return new BindPattern.Alt(alternatives);
    }

    private BindPattern.Alt.Alternative parseSinglePattern() {
        BindPattern pattern = parsePatternCore();
        boolean optional = false;
        if (check(JqTokenType.QUESTION)) {
            advance();
            optional = true;
        }
        return new BindPattern.Alt.Alternative(pattern, optional);
    }

    private BindPattern parsePatternCore() {
        JqTokenType type = peek().getType();
        if (type == JqTokenType.IDENT && peek().getText().startsWith("$")) {
            return new BindPattern.Var(advance().getText());
        }
        if (type == JqTokenType.LBRACKET) {
            advance();
            if (check(JqTokenType.RBRACKET)) {
                throw error("Empty array destructuring pattern is not allowed");
            }
            List<BindPattern.Array.Element> elements = new ArrayList<>();
            elements.add(parsePatternElement());
            while (check(JqTokenType.COMMA)) {
                advance();
                elements.add(parsePatternElement());
            }
            expect(JqTokenType.RBRACKET);
            return new BindPattern.Array(elements);
        }
        if (type == JqTokenType.LBRACE) {
            advance();
            if (check(JqTokenType.RBRACE)) {
                throw error("Empty object destructuring pattern is not allowed");
            }
            List<BindPattern.Object.Entry> entries = new ArrayList<>();
            entries.add(parsePatternEntry());
            while (check(JqTokenType.COMMA)) {
                advance();
                entries.add(parsePatternEntry());
            }
            expect(JqTokenType.RBRACE);
            return new BindPattern.Object(entries);
        }
        throw error("Invalid destructuring pattern at: " + peek().getText());
    }

    private BindPattern.Array.Element parsePatternElement() {
        BindPattern pattern = parsePatternCore();
        boolean optional = false;
        if (check(JqTokenType.QUESTION)) {
            advance();
            optional = true;
        }
        return new BindPattern.Array.Element(pattern, optional);
    }

    private BindPattern.Object.Entry parsePatternEntry() {
        JqTokenType type = peek().getType();
        boolean optional;
        BindPattern value;
        if (type == JqTokenType.IDENT && peek().getText().startsWith("$")) {
            String name = advance().getText();
            if (check(JqTokenType.COLON)) {
                // {$b: pattern} => field "b" bound to $b and destructured by pattern
                advance();
                value = parsePatternCore();
                optional = false;
                if (check(JqTokenType.QUESTION)) {
                    advance();
                    optional = true;
                }
                return new BindPattern.Object.Entry(name.substring(1), null, name,
                        value, optional);
            }
            // {$foo} shorthand
            return new BindPattern.Object.Entry(name.substring(1), null, name, null, false);
        }
        String key;
        JqAstNode keyExpr = null;
        if (type == JqTokenType.IDENT || isKeyword(type)) {
            key = advance().getText();
        } else if (type == JqTokenType.STRING) {
            JqToken token = advance();
            key = decodeStringEscapes(token.getText(), token.getPosition());
        } else if (type == JqTokenType.LPAREN) {
            advance();
            key = null;
            keyExpr = parsePipe();
            expect(JqTokenType.RPAREN);
        } else {
            throw error("Invalid object pattern key: " + peek().getText());
        }
        if (check(JqTokenType.COLON)) {
            advance();
            value = parsePatternCore();
            optional = false;
            if (check(JqTokenType.QUESTION)) {
                advance();
                optional = true;
            }
        } else {
            if (keyExpr != null)
                throw error("Pattern key expression requires ':'");
            // shorthand {str} binds $str
            String varName = "$" + key;
            value = new BindPattern.Var(varName);
            optional = false;
        }
        return new BindPattern.Object.Entry(key, keyExpr, null, value, optional);
    }

    // ---- helpers ----

    private boolean isKeyword(JqTokenType type) {
        return switch (type) {
            case LENGTH, KEYS, VALUES, TYPE, EMPTY, NULL, TRUE, FALSE,
                 SELECT, MAP, REDUCE, IF, THEN, ELSE, ELIF, END, AS,
                 TRY, CATCH, AND, OR, NOT, RECURSE, LIMIT, LABEL, BREAK,
                 DEF, IMPORT, MODULE, INPUT, INPUTS, DEBUG, ERROR, ENV,
                 FOREACH, UNTIL, WHILE -> true;
            default -> false;
        };
    }

    /**
     * Keywords that may also appear as field names (.length) or bare builtins.
     * Reserved syntax words (as/if/then/.../and/or/reduce/...) are excluded so
     * that `. as $x` is not parsed as field access `.as`.
     */
    private boolean isFieldKeyword(JqTokenType type) {
        return switch (type) {
            case LENGTH, KEYS, VALUES, TYPE, EMPTY, SELECT, MAP, RECURSE,
                 LIMIT, INPUT, INPUTS, DEBUG, ERROR, ENV, NOT -> true;
            default -> false;
        };
    }

    private boolean check(JqTokenType type) {
        return pos < tokens.size() && tokens.get(pos).getType() == type;
    }

    private boolean checkAny(JqTokenType... types) {
        if (pos >= tokens.size())
            return false;
        JqTokenType current = tokens.get(pos).getType();
        for (JqTokenType t : types) {
            if (current == t)
                return true;
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
            throw error("Expected " + type + " but got " + peek().getType()
                    + " ('" + peek().getText() + "')");
        }
        return advance();
    }

    private JqRuntimeException error(String message) {
        return new JqRuntimeException("Parse error: " + message + " at position " + pos);
    }
}
