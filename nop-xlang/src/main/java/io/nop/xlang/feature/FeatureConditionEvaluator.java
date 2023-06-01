/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.feature;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.MathHelper;
import io.nop.core.lang.eval.IPredicateEvaluator;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.expr.simple.AbstractPredicateExprParser;

import java.util.List;

public class FeatureConditionEvaluator implements IPredicateEvaluator {
    public static final FeatureConditionEvaluator INSTANCE = new FeatureConditionEvaluator();

    static final ConditionParser s_parser = new ConditionParser();

    public boolean evaluate(SourceLocation loc, String text) {
        return ConvertHelper.toTruthy(s_parser.parseExpr(loc, text));
    }

    static class ConditionParser extends AbstractPredicateExprParser<Object> {
        @Override
        protected Object newLogicExpr(SourceLocation loc, XLangOperator op, List<Object> exprs) {
            if (op == XLangOperator.AND) {
                for (Object expr : exprs) {
                    if (ConvertHelper.toFalsy(expr)) {
                        return false;
                    }
                }
                return true;
            } else {
                for (Object expr : exprs) {
                    if (ConvertHelper.toTruthy(expr)) {
                        return true;
                    }
                }
                return false;
            }
        }

        @Override
        protected Object newBinaryExpr(SourceLocation loc, XLangOperator op, Object v1, Object v2) {
            switch (op) {
                case LT:
                    return MathHelper.lt(v1, v2);
                case LE:
                    return MathHelper.le(v1, v2);
                case EQ:
                    return MathHelper.eq(v1, v2);
                case NE:
                    return !MathHelper.eq(v1, v2);
                case GE:
                    return MathHelper.ge(v1, v2);
                case GT:
                    return MathHelper.gt(v1, v2);
            }
            return null;
        }

        @Override
        protected Object newLiteralExpr(SourceLocation loc, Object value) {
            if(value == null)
                value = "";
            return value;
        }

        @Override
        protected Object newUnaryExpr(SourceLocation loc, XLangOperator op, Object x) {
            return !ConvertHelper.toTruthy(x);
        }

        @Override
        protected Object tokenExpr(TextScanner sc) {
            String name = sc.nextConfigVar();
            sc.skipBlank();
            // 作为表达式返回，因此不能为null
            return AppConfig.var(name,"");
        }
    }
}