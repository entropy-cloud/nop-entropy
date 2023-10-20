/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core.expr;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.query.FilterOp;
import io.nop.xlang.ast.AssertOpExpression;
import io.nop.xlang.ast.CompareOpExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.expr.ExprFeatures;
import io.nop.xlang.expr.simple.SimpleExprParser;

import java.util.Arrays;
import java.util.Iterator;

import static io.nop.xlang.XLangErrors.ERR_EXPR_UNEXPECTED_CHAR;

public class RuleExprParser extends SimpleExprParser {
    private final String varName;

    public RuleExprParser(String varName) {
        this.varName = Guard.notEmpty(varName, "varName");
        setUseEvalException(true);
        enableFeatures(ExprFeatures.ALL);
    }

    @Override
    public Expression parseExpr(SourceLocation loc, String text) {
        String str = text.trim();
        if (str.isEmpty() || str.equals("-")) {
            return Literal.booleanValue(loc, true);
        }

        Expression valueExpr = null;

        if (str.equals("true") || str.equals("false")) {
            valueExpr = Literal.booleanValue(loc, ConvertHelper.toBoolean(str));
        } else if (StringHelper.isNumber(str)) {
            valueExpr = Literal.numberValue(loc, StringHelper.parseNumber(str));
        } else if (StringHelper.isValidSimpleVarName(str)) {
            valueExpr = Literal.stringValue(loc, str);
        } else if (StringHelper.isQuotedString(str)) {
            valueExpr = Literal.stringValue(loc, StringHelper.unquote(str));
        }

        if (valueExpr != null) {
            return newBinaryExpr(loc, XLangOperator.EQ, XLangASTBuilder.buildPropExpr(loc, varName), valueExpr);
        }

        return super.parseExpr(loc, text);
    }

    @Override
    public Expression simpleExpr(TextScanner sc) {
        Expression x = ruleExpr(sc);
        if (x == null) {
            return super.simpleExpr(sc);
        }

        x = restRuleAndExpr(sc, x);
        return restRuleOrExpr(sc, x);
    }

    protected Expression restRuleOrExpr(TextScanner sc, Expression x) {
        if (consumeOrOp(sc)) {
            Expression y = ruleExpr(sc);
            checkRightValue(sc, XLangOperator.AND.getText(), y);
            y = restRuleAndExpr(sc, y);
            return restRuleOrExpr(sc, y);
        } else {
            return x;
        }
    }

    protected Expression restRuleAndExpr(TextScanner sc, Expression x) {
        if (consumeAndOp(sc)) {
            SourceLocation loc = sc.location();
            Expression y = ruleExpr(sc);
            checkRightValue(sc, XLangOperator.AND.getText(), y);
            y = restRuleAndExpr(sc, y);
            return newLogicExpr(loc, XLangOperator.AND, Arrays.asList(x, y));
        } else {
            return x;
        }
    }

    private Expression ruleExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        boolean not = sc.tryMatchToken("not");
        SourceLocation loc2 = sc.location();

        Expression ret = null;
        XLangOperator op = this.peekOperator(sc);
        if (op != null) {
            if (op.isCompareOp() || op.isEqualityOp()) {
                skipOp(sc);
                Expression y = inclusiveOrExpr(sc);
                checkRightValue(sc, op.getText(), y);
                ret = newBinaryExpr(loc2, op, XLangASTBuilder.buildPropExpr(loc, varName), y);
            } else {
                throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
            }
        } else if (sc.cur != '(') {
            Iterator<FilterOp> it = FilterOp.opIterator();
            while (it.hasNext()) {
                FilterOp filterOp = it.next();
                if (sc.tryMatchToken(filterOp.name())) {
                    return parseFilterOpExpr(loc2, filterOp, sc);
                }
            }
        }

        if (not) {
            if (ret == null) {
                ret = unaryExpr(sc);
                checkUnaryExpr(sc, XLangOperator.NOT, ret);
            }
            ret = newUnaryExpr(loc, XLangOperator.NOT, ret);
        }

        return ret;
    }

    private Expression parseFilterOpExpr(SourceLocation loc, FilterOp filterOp, TextScanner sc) {
        switch (filterOp.getType()) {
            case ASSERT_OP:
                return AssertOpExpression.valueOf(loc, filterOp, XLangASTBuilder.buildPropExpr(loc, varName));
            case FIXED_VALUE:
                return Literal.booleanValue(loc, filterOp == FilterOp.ALWAYS_TRUE);
            case GROUP_OP:
                throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
            case COMPARE_OP: {
                Expression y = inclusiveOrExpr(sc);
                checkRightValue(sc, filterOp.name(), y);
                return CompareOpExpression.valueOf(loc, XLangASTBuilder.buildPropExpr(loc, varName), filterOp, y);
            }
            case BETWEEN_OP:
            default: {
                throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
            }
        }
    }
}