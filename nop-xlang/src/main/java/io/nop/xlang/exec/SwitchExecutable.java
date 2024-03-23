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
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.Objects;

public class SwitchExecutable extends AbstractExecutable {
    private final boolean asExpr;
    private final IExecutableExpression discriminant;
    private final IExecutableExpression[] tests;
    private final IExecutableExpression[] consequences;
    private final boolean[] fallthroughs;
    private final IExecutableExpression defaultCase;

    public SwitchExecutable(SourceLocation loc, boolean asExpr,
                            IExecutableExpression discriminant,
                            IExecutableExpression[] tests,
                            IExecutableExpression[] consequences,
                            boolean[] fallthroughs,
                            IExecutableExpression defaultCase) {
        super(loc);
        this.asExpr = asExpr;
        this.discriminant = Guard.notNull(discriminant, "discriminant");
        this.tests = Guard.notNull(tests, "tests");
        this.consequences = Guard.notNull(consequences, "consequences");
        this.fallthroughs = Guard.notNull(fallthroughs, "fallthroughs");
        this.defaultCase = defaultCase;
    }

    public boolean containsReturnStatement() {
        for (IExecutableExpression consequent : consequences) {
            if (consequent.containsReturnStatement())
                return true;
        }
        if (defaultCase != null)
            return defaultCase.containsReturnStatement();
        return false;
    }

    public boolean containsBreakStatement() {
        for (IExecutableExpression consequent : consequences) {
            if (consequent.containsBreakStatement())
                return true;
        }
        if (defaultCase != null)
            return defaultCase.containsBreakStatement();
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object value = executor.execute(discriminant, rt);
        Object ret = null;
        for (int i = 0, n = tests.length; i < n; i++) {
            Object testValue = executor.execute(tests[i], rt);
            if (Objects.equals(value, testValue)) {
                ret = executor.execute(consequences[i], rt);
                if (!fallthroughs[i]) {
                    return asExpr ? ret : null;
                }
            }
        }
        if (defaultCase != null) {
            ret = executor.execute(defaultCase, rt);
        }
        return asExpr ? ret : null;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("switch(");
        discriminant.display(sb);
        sb.append("){}");
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            discriminant.visit(visitor);
            for (IExecutableExpression test : tests) {
                test.visit(visitor);
            }
            for (IExecutableExpression consequent : consequences) {
                consequent.visit(visitor);
            }
            if (defaultCase != null)
                defaultCase.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}