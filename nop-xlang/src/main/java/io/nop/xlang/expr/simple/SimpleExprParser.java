/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.expr.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.parse.GenericTypeParser;
import io.nop.core.type.parse.IGenericTypeParser;
import io.nop.xlang.ast.ArrayExpression;
import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.ast.BraceExpression;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.ChainExpression;
import io.nop.xlang.ast.ConcatExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.InstanceOfExpression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.LogicalExpression;
import io.nop.xlang.ast.MacroExpression;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.NamedTypeNode;
import io.nop.xlang.ast.NewExpression;
import io.nop.xlang.ast.ObjectCallExpression;
import io.nop.xlang.ast.ObjectExpression;
import io.nop.xlang.ast.ParameterDeclaration;
import io.nop.xlang.ast.PropertyAssignment;
import io.nop.xlang.ast.PropertyKind;
import io.nop.xlang.ast.SequenceExpression;
import io.nop.xlang.ast.SpreadElement;
import io.nop.xlang.ast.TemplateExpression;
import io.nop.xlang.ast.UnaryExpression;
import io.nop.xlang.ast.UpdateExpression;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.expr.ExprFeatures;
import io.nop.xlang.expr.ExprPhase;
import io.nop.xlang.expr.IExpressionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.nop.commons.CommonErrors.ERR_SCAN_UNEXPECTED_CHAR;
import static io.nop.xlang.XLangErrors.ARG_EXPECTED;
import static io.nop.xlang.XLangErrors.ERR_EXPR_NOT_ALLOW_CP_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXPR_NOT_CP_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXPR_NOT_SINGLE_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXPR_UNEXPECTED_CHAR;
import static io.nop.xlang.ast.XLangASTHelper.allowCall;
import static io.nop.xlang.ast.XLangASTHelper.allowMandatoryChain;
import static io.nop.xlang.ast.XLangASTHelper.allowMember;

public class SimpleExprParser extends AbstractExprParser<Expression> implements IExpressionParser {
    static final Logger LOG = LoggerFactory.getLogger(SimpleExprParser.class);

    private IGenericTypeParser typeParser = new GenericTypeParser();

    private boolean subExpr;

    private boolean intern;

    public static SimpleExprParser newDefault() {
        SimpleExprParser parser = new SimpleExprParser();
        parser.setUseEvalException(true);
        parser.enableFeatures(ExprFeatures.ALL);
        return parser;
    }

    public SimpleExprParser subExpr(boolean subExpr) {
        this.subExpr = subExpr;
        return this;
    }

    public SimpleExprParser enableFeatures(int features) {
        addFeatures(features);
        return this;
    }

    public SimpleExprParser useEvalException(boolean b) {
        setUseEvalException(b);
        return this;
    }

    public SimpleExprParser intern(boolean shouldIntern) {
        this.intern = shouldIntern;
        this.typeParser.intern(shouldIntern);
        return this;
    }

    public boolean shouldIntern() {
        return intern;
    }

    protected String internToken(String token) {
        if (intern)
            return token.intern();
        return token;
    }

    public Expression parseExpr(TextScanner sc) {
        Expression ret = makeCompileResult(simpleExpr(sc));

        if (!subExpr)
            sc.checkEnd();
        return ret;
    }

    public Expression parseTemplateExpr(SourceLocation loc, String source, boolean singleExpr, ExprPhase phase) {
        if (StringHelper.isEmpty(source))
            return null;

        TextScanner sc = TextScanner.fromString(loc, source);
        sc.useEvalException = isUseEvalException();

        List<Expression> list = new ArrayList<>();
        MutableString text = sc.nextUntil(s -> isExprStart(s, phase), true, "");
        if (text.length() > 0) {
            if (singleExpr)
                throw sc.newError(ERR_EXPR_NOT_SINGLE_EXPR);

            list.add(newLiteralExpr(loc, text.toString()));
        }

        while (isExprStart(sc, phase)) {
            boolean cpExpr = sc.cur == '#';
            checkAllowMacroExpr(sc);
            skipExprStart(sc);
            sc.skipBlank();

            if (phase == ExprPhase.compile) {
                if (!cpExpr)
                    throw sc.newError(ERR_EXPR_NOT_CP_EXPR);
            }

            if (singleExpr && !list.isEmpty())
                throw sc.newError(ERR_EXPR_NOT_SINGLE_EXPR);

            Expression expr = simpleExpr(sc);
            if (cpExpr)
                expr = macroExpr(expr);
            if (expr != null) {
                list.add(expr);
            }
            consumeExprEnd(sc);

            text = sc.nextUntil(s -> isExprStart(sc, phase), true, "");
            if (text.length() > 0) {
                if (singleExpr)
                    throw sc.newError(ERR_EXPR_NOT_SINGLE_EXPR);
                list.add(newLiteralExpr(loc, text.toString()));
            }
        }

        sc.checkEnd();

        // 如果只包含一个元素，则要么是文本输出，要么是唯一的EL表达式
        if (list.size() == 1)
            return makeCompileResult(list.get(0));

        return makeCompileResult(newTemplateExpr(loc, list, phase));
    }

    protected void skipExprStart(TextScanner sc) {
        sc.next(2);
    }

    protected void consumeExprEnd(TextScanner sc) {
        sc.consume('}');
    }

    protected void checkAllowMacroExpr(TextScanner sc) {
        if (sc.cur == '#' && sc.peek() == '{' && !supportFeature(ExprFeatures.CP_EXPR))
            throw sc.newError(ERR_EXPR_NOT_ALLOW_CP_EXPR);
    }

    protected Expression simpleExpr(TextScanner sc) {
        return ternaryExpr(sc);
    }

    protected Expression macroExpr(Expression expr) {
        if (expr == null)
            return null;
        return MacroExpression.valueOf(expr.getLocation(), expr);
    }

    public boolean isExprStart(TextScanner sc, ExprPhase phase) {
        switch (phase) {
            case transform:
                return sc.cur == '%' && sc.peek() == '{';
            case compile:
                return sc.cur == '#' && sc.peek() == '{';
            case binding:
                return sc.cur == '@' && sc.peek() == '{';
            default:
                return (sc.cur == '$' || sc.cur == '#') && sc.peek() == '{';
        }
    }

    protected Expression ternaryExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        Expression x = nullCoalesceExpr(sc);
        if (mayMatch(sc, XLangOperator.QUESTION)) {
            checkFactor(sc, XLangOperator.QUESTION.name(), x);
            Expression y = nullCoalesceExpr(sc);
            checkFactor(sc, XLangOperator.QUESTION.name(), y);
            sc.match(':');
            Expression z = ternaryExpr(sc);

            return newTernaryExpr(loc, x, y, z);
        }
        return x;
    }

    protected Expression nullCoalesceExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        Expression x = orExpr(sc);
        if (mayMatch(sc, XLangOperator.NULL_COALESCE)) {
            checkLeftValue(sc, XLangOperator.NULL_COALESCE.name(), x);
            Expression y = nullCoalesceExpr(sc);
            checkRightValue(sc, XLangOperator.NULL_COALESCE.name(), y);
            x = newNullCoalesceExpr(loc, x, y);
        }
        return x;
    }

    /**
     * <pre>
     *   InclusiveOrExpression :=
     *     ExclusiveOrExpression { '|' ExclusiveOrExpression }
     * </pre>
     */
    protected Expression inclusiveOrExpr(TextScanner sc) {
        Expression x = exclusiveOrExpr(sc);
        if (supportFeature(ExprFeatures.BIT_OP)) {
            while (mayMatch(sc, XLangOperator.BIT_OR)) {
                checkLeftValue(sc, XLangOperator.BIT_OR.name(), x);
                Expression y = exclusiveOrExpr(sc);
                checkRightValue(sc, XLangOperator.BIT_OR.name(), y);
                x = newBinaryExpr(x.getLocation(), XLangOperator.BIT_OR, x, y);
            }
        }
        return x;
    }

    /**
     * <pre>
     *   ExclusiveOrExpression :=
     *     AndExpression { '^' AndExpression }
     * </pre>
     */
    protected Expression exclusiveOrExpr(TextScanner sc) {
        Expression x = bitAndExpr(sc);
        if (supportFeature(ExprFeatures.BIT_OP)) {
            while (mayMatch(sc, XLangOperator.BIT_XOR)) {
                checkLeftValue(sc, XLangOperator.BIT_XOR.name(), x);

                Expression y = bitAndExpr(sc);

                checkRightValue(sc, XLangOperator.BIT_XOR.name(), y);

                x = newBinaryExpr(x.getLocation(), XLangOperator.BIT_XOR, x, y);
            }
        }
        return x;
    }

    protected Expression bitAndExpr(TextScanner sc) {
        Expression x = equalityExpr(sc);
        if (supportFeature(ExprFeatures.BIT_OP)) {
            while (mayMatch(sc, XLangOperator.BIT_AND)) {
                checkLeftValue(sc, XLangOperator.BIT_AND.name(), x);

                Expression y = equalityExpr(sc);
                checkRightValue(sc, XLangOperator.BIT_AND.name(), y);

                x = newBinaryExpr(x.getLocation(), XLangOperator.BIT_AND, x, y);
            }
        }
        return x;
    }

    @Override
    protected Expression relationalExprEx(TextScanner sc, Expression x) {
        if (sc.tryMatchToken("instanceof")) {
            NamedTypeNode type = typeReference(sc);
            return newInstanceOfExpr(x.getLocation(), x, type);
        }
        return null;
    }

    protected NamedTypeNode typeReference(TextScanner sc) {
        SourceLocation loc = sc.location();
        String typeName = sc.nextJavaPropPath();
        return XLangASTBuilder.typeName(loc, typeName);
    }

    protected Expression shiftExpr(TextScanner sc) {
        Expression x = additiveExpr(sc);

        if (supportFeature(ExprFeatures.BIT_OP)) {
            XLangOperator op = peekOperator(sc);
            if (op != null && op.isBitOp()) {
                checkLeftValue(sc, op.name(), x);
                skipOp(sc);
                Expression y = additiveExpr(sc);
                checkRightValue(sc, op.name(), y);
                return newBinaryExpr(x.getLocation(), op, x, y);
            }
        }

        return x;
    }

    /**
     * <pre>
     *   AdditiveExpression :=
     *     MultiplicativeExpression { ( '+' | '-' ) MultiplicativeExpression }
     * </pre>
     */
    protected Expression additiveExpr(TextScanner sc) {
        Expression x = multiplicativeExpr(sc);
        XLangOperator op;
        while ((op = peekOperator(sc)) != null && op.isAdditiveOp()) {
            skipOp(sc);

            if (sc.cur == '!' || sc.cur == '-') {
                throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
            }

            checkLeftValue(sc, op.name(), x);
            Expression y = multiplicativeExpr(sc);
            checkRightValue(sc, op.name(), y);
            x = newBinaryExpr(x.getLocation(), op, x, y);
        }
        return x;
    }

    /**
     * <pre>
     *   MultiplicativeExpression :=
     *     UnaryExpression { ( '*' | '/' | '%' ) UnaryExpression }
     * </pre>
     */
    protected Expression multiplicativeExpr(TextScanner sc) {
        Expression x = unaryExpr(sc);
        XLangOperator op;
        while ((op = peekOperator(sc)) != null && op.isMultiplicativeOp()) {
            skipOp(sc);
            checkLeftValue(sc, op.name(), x);
            Expression y = unaryExpr(sc);
            checkRightValue(sc, op.name(), y);
            x = newBinaryExpr(x.getLocation(), op, x, y);
        }
        return x;
    }

    protected Expression unaryExpr(TextScanner sc) {
        if (mayMatch(sc, XLangOperator.NOT)) {
            Expression x = unaryExpr(sc);
            checkUnaryExpr(sc, XLangOperator.NOT, x);
            return newUnaryExpr(x.getLocation(), XLangOperator.NOT, x);
        } else if (supportFeature(ExprFeatures.BIT_OP) && mayMatch(sc, XLangOperator.BIT_NOT)) {
            Expression x = unaryExpr(sc);
            checkUnaryExpr(sc, XLangOperator.BIT_NOT, x);
            return newUnaryExpr(x.getLocation(), XLangOperator.BIT_NOT, x);
        }

        if (mayMatch(sc, XLangOperator.MINUS)) {
            Expression x = factorExpr(sc);
            checkUnaryExpr(sc, XLangOperator.MINUS, x);
            return newUnaryExpr(x.getLocation(), XLangOperator.MINUS, x);
        } else {
            Expression x = factorExpr(sc);
            return x;
        }
    }

    protected Expression factorExpr(TextScanner sc) {
        if (sc.cur == '#') {
            checkAllowMacroExpr(sc);
            if (sc.peek() == '{')
                return cpExpr(sc);

            // if (this.supportFeature(ExprFeatures.TAG_FUNC)) {
            // if (sc.peek() == '[')
            // return tagFuncExpr(sc);
            // }
            throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
        }

        Expression x = primaryExpr(sc);
        if (x == null)
            return null;
        return factorRestExpr(sc, x);
    }

    protected Expression factorRestExpr(TextScanner sc, Expression x) {
        while (true) {
            if (sc.cur == '!') {
                if (sc.peek(1) == '=')
                    return x;
                checkCondition(sc, allowMandatoryChain(x));
                sc.match('!');
                x = ChainExpression.valueOf(x.getLocation(), x, false);
            } else if (sc.startsWith("?.")) {
                sc.next(2);
                sc.skipBlank();
                sc.peekToken = null;
                x = attrExpr(sc, x, true);
            } else if (sc.cur == '.' || sc.cur == '[' || sc.cur == '(') {
                x = attrExpr(sc, x, false);
            } else {
                break;
            }
        }

        if (supportFeature(ExprFeatures.SELF_INC)) {
            if (mayMatch(sc, XLangOperator.SELF_INC)) {
                x = newSelfIncExpr(x.getLocation(), true, x);
            } else if (mayMatch(sc, XLangOperator.SELF_DEC)) {
                x = newSelfIncExpr(x.getLocation(), false, x);
            }
        }
        return x;
    }

    protected boolean isStringStart(TextScanner sc){
        return sc.cur == '\'' || sc.cur == '"';
    }

    protected Expression primaryExpr(TextScanner sc) {
        if (isStringStart(sc)) {
            Expression x = stringExpr(sc);
            return x;
        }

        if (sc.cur == '(') {
            SourceLocation loc = sc.location();
            sc.match('(');
            Expression x = simpleExpr(sc);
            if (x == null)
                throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
            if (sc.cur == ',') {
                if (x instanceof Identifier) {
                    List<Identifier> names = new ArrayList<>();
                    names.add((Identifier) x);
                    while (sc.tryMatch(',')) {
                        names.add(tokenExpr(sc));
                    }
                    sc.match(')');
                    checkCondition(sc, mayMatch(sc, XLangOperator.ARROW));
                    Expression body = simpleExpr(sc);
                    checkCondition(sc, body != null);
                    return newArrowFuncExpr(loc, names, body);
                }
            } else {
                if (x instanceof Identifier) {
                    sc.match(')');
                    return arrowFuncExpr(sc, (Identifier) x);
                }
            }
            sc.match(')');
            return newBraceExpr(loc, x);
        }

        if (StringHelper.isDigit(sc.cur)) {
            Expression x = numberExpr(sc);
            if (sc.cur == '[' || sc.cur == '(' || sc.cur == '.')
                throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
            return x;
        }

        if (supportFeature(ExprFeatures.JSON)) {
            if (sc.cur == '[') {
                return arrayExpr(sc);
            }
            if (sc.cur == '{') {
                return objectExpr(sc);
            }
        }

        if (Character.isJavaIdentifierStart(sc.cur)) {
            SourceLocation loc = sc.location();

            if (sc.tryMatchToken("true"))
                return newLiteralExpr(loc, true);

            if (sc.tryMatchToken("false")) {
                return newLiteralExpr(loc, false);
            }

            if (sc.tryMatchToken("null")) {
                return newLiteralExpr(loc, null);
            }

            if (supportFeature(ExprFeatures.NEW)) {
                if (sc.tryMatchToken("new")) {
                    return newObjectExpr(sc);
                }
            }

            return varFactorExpr(sc);
        }
        return defaultFactorExpr(sc);
    }

    protected Expression varFactorExpr(TextScanner sc) {
        return arrowFuncExpr(sc, tokenExpr(sc));
    }

    protected Expression arrowFuncExpr(TextScanner sc, Identifier x) {
        if (this.mayMatch(sc, XLangOperator.ARROW)) {
            Expression body = simpleExpr(sc);
            checkCondition(sc, body != null);
            return newArrowFuncExpr(x.getLocation(), x, body);
        } else {
            return x;
        }
    }

    protected Expression cpExpr(TextScanner sc) {
        sc.match("#{");
        Expression x = orExpr(sc);
        sc.match('}');
        return x;
    }

    protected Expression attrExpr(TextScanner sc, Expression x, boolean optional) {
        SourceLocation loc = sc.location();
        if (sc.cur == '(') {
            checkCondition(sc, allowCall(x));
            List<Expression> args = argsExpr(sc);
            return newFunctionExpr(loc, x, args, optional);
        } else if (supportFeature(ExprFeatures.ARRAY_INDEX) && sc.tryMatch('[')) {
            checkCondition(sc, allowMember(x));
            Expression attrExpr = simpleExpr(sc);
            sc.match(']');
            return newAttrExpr(loc, x, attrExpr, optional);
        } else if (supportFeature(ExprFeatures.OBJECT_PROP) && (optional || sc.tryMatch('.'))) {
            checkCondition(sc, allowMember(x));

            Identifier attr = idExpr(sc);
            if (supportFeature(ExprFeatures.OBJECT_CALL) && sc.cur == '(') {
                List<Expression> args = argsExpr(sc);
                x = newObjectFunctionExpr(loc, x, attr, args, optional);
                return x;
            } else {
                x = newPropExpr(loc, x, attr, optional);
                return x;
            }
        } else {
            throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
        }
    }

    protected Expression arrayExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        sc.match('[');
        if (sc.tryMatch(']'))
            return newArrayExpr(loc, Collections.emptyList());

        List<XLangASTNode> list = new ArrayList<>();

        do {
            if (sc.cur == ']')
                break;

            if (sc.startsWith("...")) {
                list.add(spread(sc));
            } else {
                Expression x = simpleExpr(sc);
                list.add(x);
            }
        } while (sc.tryMatch(','));
        sc.match(']');
        return newArrayExpr(loc, list);
    }

    protected Expression objectExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        sc.match('{');

        if (sc.tryMatch('}'))
            return newMapExpr(loc, Collections.emptyList());

        List<XLangASTNode> list = new ArrayList<>();

        do {
            if (sc.cur == '}')
                break;

            if (sc.startsWith("...")) {
                list.add(spread(sc));
            } else {
                boolean computed = sc.cur == '[';
                boolean token = !computed && sc.cur != '\'' && sc.cur != '"';
                Expression x = objectKeyExpr(sc);
                Expression y;
                if (sc.tryMatch(':')) {
                    y = simpleExpr(sc);
                } else if (token) {
                    // {x,y}这种简写语法
                    Literal literal = (Literal) x;
                    y = Identifier.valueOf(literal.getLocation(), literal.getStringValue());
                } else {
                    throw sc.newError(ERR_SCAN_UNEXPECTED_CHAR).param(ARG_EXPECTED, ':');
                }
                PropertyAssignment expr = newPropertyExpr(x.getLocation(), x, y);
                expr.setComputed(computed);
                list.add(expr);
            }
        } while (sc.tryMatch(','));

        sc.match('}');
        return newMapExpr(loc, list);
    }

    protected SpreadElement spread(TextScanner sc) {
        SourceLocation loc = sc.location();
        sc.match("...");
        if (sc.cur == '.')
            throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
        Expression x = simpleExpr(sc);
        return SpreadElement.valueOf(loc, x);
    }

    protected Expression objectKeyExpr(TextScanner sc) {
        if (sc.cur == '[') {
            sc.next();
            Expression x = factorExpr(sc);
            sc.match(']');
            return x;
        }
        if (sc.cur == '\'' || sc.cur == '"')
            return stringExpr(sc);

        SourceLocation loc = sc.location();
        String name = sc.nextJavaVar();
        sc.skipBlank();
        name = internToken(name);
        return newLiteralExpr(loc, name);
    }

    protected List<Expression> argsExpr(TextScanner sc) {
        sc.match('(');
        if (sc.cur == ')') {
            sc.next();
            sc.skipBlank();
            return Collections.emptyList();
        }

        List<Expression> exprs = new ArrayList<>();
        do {
            Expression x = simpleExpr(sc);
            exprs.add(x);
        } while (sc.tryMatch(','));

        sc.match(')');

        return exprs;
    }

    protected Identifier tokenExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        String name = sc.nextJavaVar();
        sc.skipBlank();
        name = internToken(name);
        return newTokenExpr(loc, name);
    }

    protected Identifier idExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        String name = sc.nextJavaVar();
        sc.skipBlank();
        name = internToken(name);
        return newIdExpr(loc, name);
    }

    protected Expression newObjectExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        NamedTypeNode type = typeReference(sc);
        sc.skipBlank();
        List<Expression> argExprs = argsExpr(sc);
        return newNewExpr(loc, type, argExprs);
    }

//    protected void validateNotKeyword(TextScanner sc, SourceLocation loc, String varName) {
//        if (ExprConstants.KEYWORDS.contains(varName)) {
//            throw sc.newError(ERR_EXPR_TOKEN_SHOULD_NOT_BE_KEYWORD).loc(loc).param(ARG_TOKEN, varName);
//        }
//    }

    protected Expression newInstanceOfExpr(SourceLocation loc, Expression value, NamedTypeNode refType) {
        InstanceOfExpression expr = new InstanceOfExpression();
        expr.setLocation(loc);
        expr.setValue(value);
        expr.setRefType(refType);
        return expr;
    }

    protected Expression newArrayExpr(SourceLocation loc, List<XLangASTNode> argExprs) {
        ArrayExpression expr = new ArrayExpression();
        expr.setLocation(loc);
        expr.setElements(argExprs);
        return expr;
    }

    protected PropertyAssignment newPropertyExpr(SourceLocation loc, Expression key, Expression value) {
        PropertyAssignment expr = new PropertyAssignment();
        expr.setLocation(loc);
        expr.setKind(PropertyKind.init);
        expr.setKey(key);
        expr.setValue(value);
        expr.setComputed(!(key instanceof Literal));
        return expr;
    }

    protected Expression newMapExpr(SourceLocation loc, List<XLangASTNode> argExprs) {
        ObjectExpression expr = new ObjectExpression();
        expr.setLocation(loc);
        expr.setProperties(argExprs);
        return expr;
    }

    protected Expression newLiteralExpr(SourceLocation loc, Object value) {
        Literal literal = new Literal();
        literal.setLocation(loc);
        literal.setValue(value);
        return literal;
    }

    protected Expression newAttrExpr(SourceLocation loc, Expression x, Expression attrExpr, boolean optional) {
        MemberExpression expr = new MemberExpression();
        expr.setOptional(optional);
        expr.setLocation(loc);
        expr.setObject(x);
        expr.setProperty(attrExpr);
        expr.setComputed(true);
        return expr;
    }

    protected Identifier newTokenExpr(SourceLocation loc, String name) {
        Identifier id = new Identifier();
        id.setLocation(loc);
        id.setName(name);
        return id;
    }

    protected Identifier newIdExpr(SourceLocation loc, String name) {
        Identifier id = new Identifier();
        id.setLocation(loc);
        id.setName(name);
        return id;
    }

    protected Expression newSelfIncExpr(SourceLocation loc, boolean add, Expression x) {
        UpdateExpression expr = new UpdateExpression();
        expr.setLocation(loc);
        expr.setOperator(add ? XLangOperator.SELF_INC : XLangOperator.SELF_DEC);
        expr.setArgument(x);
        return expr;
    }

    protected Expression newBinaryExpr(SourceLocation loc, XLangOperator op, Expression x, Expression y) {
        BinaryExpression expr = new BinaryExpression();
        expr.setLocation(loc);
        expr.setOperator(op);
        expr.setLeft(x);
        expr.setRight(y);
        return expr;
    }

    protected Expression newLogicExpr(SourceLocation loc, XLangOperator op, List<Expression> exprs) {
        return newLogicExpr(loc, op, exprs, 0);
    }

    protected Expression newLogicExpr(SourceLocation loc, XLangOperator op, List<Expression> exprs, int startIndex) {
        LogicalExpression expr = new LogicalExpression();
        expr.setOperator(op);
        expr.setLeft(exprs.get(startIndex));
        if (startIndex >= exprs.size() - 2) {
            expr.setRight(exprs.get(startIndex + 1));
        } else {
            expr.setRight(newLogicExpr(loc, op, exprs, startIndex + 1));
        }
        return expr;
    }

    protected Expression newUnaryExpr(SourceLocation loc, XLangOperator op, Expression x) {
        UnaryExpression expr = new UnaryExpression();
        expr.setLocation(loc);
        expr.setOperator(op);
        expr.setArgument(x);
        return expr;
    }

    protected Expression newFunctionExpr(SourceLocation loc, Expression func, List<Expression> argList, boolean optional) {
        CallExpression expr = new CallExpression();
        expr.setLocation(loc);
        expr.setCallee(func);
        expr.setOptional(optional);
        expr.setArguments(argList);
        return expr;
    }

    protected Expression newObjectFunctionExpr(SourceLocation loc, Expression x, Identifier funcName,
                                               List<Expression> argList, boolean optional) {
        ObjectCallExpression expr = new ObjectCallExpression();
        MemberExpression callee = newPropExpr(loc, x, funcName, optional);
        expr.setLocation(loc);
        expr.setCallee(callee);
        expr.setArguments(argList);
        return expr;
    }

    protected MemberExpression newPropExpr(SourceLocation loc, Expression x, Identifier propName, boolean optional) {
        MemberExpression expr = new MemberExpression();
        expr.setOptional(optional);
        expr.setLocation(loc);
        expr.setObject(x);
        expr.setProperty(propName);
        return expr;
    }

    protected Expression newSequenceExpr(SourceLocation loc, List<Expression> exprs) {
        return SequenceExpression.valueOf(loc, exprs);
    }

    protected Expression newTemplateExpr(SourceLocation loc, List<Expression> exprs, ExprPhase phase) {
        if (exprs.isEmpty())
            return null;
        String prefix;
        switch (phase) {
            case transform:
                prefix = "%{";
                break;
            case compile:
                prefix = "#{";
                break;
            case binding:
                prefix = "@{";
                break;
            default:
                prefix = "${";
        }
        return TemplateExpression.valueOf(loc, exprs, prefix, "}");
    }

    protected Expression newConcatExpr(SourceLocation loc, List<Expression> exprs) {
        if (exprs.isEmpty())
            return null;
        return ConcatExpression.valueOf(loc, exprs);
    }


    protected Expression newBraceExpr(SourceLocation loc, Expression expr) {
        if (expr instanceof BraceExpression)
            return expr;
        return BraceExpression.valueOf(loc, expr);
    }

    // protected Expression newTagFuncExpr(SourceLocation loc, String tagName, List<XplAttrExpression> attrs) {
    // return XplNodeExpression.valueOf(loc, tagName, attrs);
    // }

    protected Expression newNewExpr(SourceLocation loc, NamedTypeNode type, List<Expression> args) {
        NewExpression expr = new NewExpression();
        expr.setLocation(loc);
        expr.setCallee(type);
        expr.setArguments(args);
        return expr;
    }

    protected Expression newArrowFuncExpr(SourceLocation loc, Identifier x, Expression body) {
        ArrowFunctionExpression expr = new ArrowFunctionExpression();
        expr.setLocation(loc);
        List<ParameterDeclaration> decls = Arrays.asList(ParameterDeclaration.valueOf(x));
        expr.setParams(decls);
        expr.setBody(body);
        return expr;
    }

    protected Expression newArrowFuncExpr(SourceLocation loc, List<Identifier> names, Expression body) {
        ArrowFunctionExpression expr = new ArrowFunctionExpression();
        expr.setLocation(loc);
        List<ParameterDeclaration> decls = new ArrayList<>(names.size());
        for (Identifier name : names) {
            decls.add(ParameterDeclaration.valueOf(name));
        }
        expr.setBody(body);
        return expr;
    }

    protected Expression newNullCoalesceExpr(SourceLocation loc, Expression x, Expression y) {
        return BinaryExpression.valueOf(loc, x, XLangOperator.NULL_COALESCE, y);
    }

    protected Expression newTernaryExpr(SourceLocation loc, Expression test, Expression x, Expression y) {
        return IfStatement.valueOf(loc, test, x, y, true);
    }
}