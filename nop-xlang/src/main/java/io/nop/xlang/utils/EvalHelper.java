/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.utils;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.MathHelper;
import io.nop.xlang.ast.XLangOperator;

public class EvalHelper {

    public static Object binaryOp(XLangOperator operator, Object v1, Object v2) {
        switch (operator) {
            case BIT_AND:
                return MathHelper.band(v1, v2);
            case BIT_OR:
                return MathHelper.bor(v1, v2);
            case BIT_XOR:
                return MathHelper.bxor(v1, v2);
            case BIT_LEFT_SHIFT:
                return MathHelper.sl(v1, v2);
            case BIT_RIGHT_SHIFT:
                return MathHelper.sr(v1, v2);
            case BIT_UNSIGNED_RIGHT_SHIFT:
                return MathHelper.usr(v1, v2);
            case ADD:
                return MathHelper.add(v1, v2);
            case MINUS:
                return MathHelper.min(v1, v2);
            case MULTIPLY:
                return MathHelper.multiply(v1, v2);
            case DIVIDE:
                return MathHelper.divide(v1, v2);
            case MOD:
                return MathHelper.mod(v1, v2);
            case LT:
                return MathHelper.lt(v1, v2);
            case LE:
                return MathHelper.le(v1, v2);
            case EQ:
                return MathHelper.xlangEq(v1, v2);
            case NE:
                return !MathHelper.xlangEq(v1, v2);
            case GE:
                return MathHelper.ge(v1, v2);
            case GT:
                return MathHelper.gt(v1, v2);
            case AND: {
                if (ConvertHelper.toTruthy(v1))
                    return v2;
                return v1;
            }
            case OR: {
                if (ConvertHelper.toTruthy(v1))
                    return v1;
                return v2;
            }
        }
        throw new UnsupportedOperationException("nop.eval.invalid-binary-operator:" + operator);
    }
}
