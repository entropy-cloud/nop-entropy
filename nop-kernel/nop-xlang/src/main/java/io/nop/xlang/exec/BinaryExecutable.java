/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.utils.EvalHelper;

public class BinaryExecutable extends AbstractBinaryExecutable {
    private final XLangOperator operator;

    protected BinaryExecutable(SourceLocation loc, XLangOperator operator, IExecutableExpression left,
                               IExecutableExpression right) {
        super(loc, left, right);
        this.operator = Guard.notNull(operator, "operator is empty");
    }

    public static IExecutableExpression valueOf(SourceLocation loc, XLangOperator operator, IExecutableExpression left,
                                                IExecutableExpression right) {
        switch (operator) {
            case ADD:
                return new PlusExecutable(loc, left, right);
            case MINUS:
                return new MinusExecutable(loc, left, right);
            case MULTIPLY:
                return new MultiplyExecutable(loc, left, right);
            case DIVIDE:
                return new DivideExecutable(loc, left, right);
            case AND:
                return new AndExecutable(loc, left, right);
            case OR:
                return new OrExecutable(loc, left, right);
            case EQ: {
                if (left instanceof NullExecutable) {
                    return new EqNullExecutable(loc, right);
                }
                if (right instanceof NullExecutable)
                    return new EqNullExecutable(loc, left);
                return new EqExecutable(loc, left, right);
            }
            case NE: {
                if (left instanceof NullExecutable) {
                    return new NeNullExecutable(loc, right);
                }
                if (right instanceof NullExecutable)
                    return new NeNullExecutable(loc, left);
                return new NeExecutable(loc, left, right);
            }
            case GT:
                return new GtExecutable(loc, left, right);
            case GE:
                return new GeExecutable(loc, left, right);
            case LT:
                return new LtExecutable(loc, left, right);
            case LE:
                return new LeExecutable(loc, left, right);
            case NULL_COALESCE:
                return new NullCoalesceExecutable(loc, left, right);
            default:
                return new BinaryExecutable(loc, operator, left, right);
        }
    }

    @Override
    public XLangOperator getOperator() {
        return operator;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object v1 = executor.execute(left, rt);
        Object v2 = executor.execute(right, rt);

        return EvalHelper.binaryOp(operator, v1, v2);
    }
}
