/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder.functions;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.model.JoinSideRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WI13: tags one join input's records with their side and equi-key before the
 * union, so the single keyed {@code EquiJoinOperator} can tell the two sides
 * apart and group by key without evaluating expressions itself. The key
 * expressions (one per join key column, from joinSpec {@code leftKeyExprs} /
 * {@code rightKeyExprs}) are evaluated here — once per record — with {@code event}
 * bound to the input element, exactly like {@code EvalActionKeySelector}.
 *
 * <p>Multi-key joins composite the per-expression results into an ordered list
 * (position-wise equality is the equi-condition, mirroring the equal-arity
 * validation in the DSL builder).
 */
public final class JoinSideTagFunction implements MapFunction<Object, JoinSideRecord<Object>> {

    private static final long serialVersionUID = 1L;

    private final boolean left;
    private final List<IEvalAction> keyExprs;

    public JoinSideTagFunction(boolean left, List<IEvalAction> keyExprs) {
        this.left = left;
        this.keyExprs = keyExprs;
    }

    @Override
    public JoinSideRecord<Object> map(Object value) {
        return new JoinSideRecord<>(left, evalKey(keyExprs, value), value);
    }

    /**
     * Evaluates the side's key expressions; a single key yields the bare value,
     * multiple keys an ordered composite list.
     */
    public static Object evalKey(List<IEvalAction> exprs, Object event) {
        if (exprs.size() == 1) {
            return exprs.get(0).invoke(XplFunctionSupport.newCallScope()
                    .newChildScope(Collections.singletonMap("event", event)));
        }
        List<Object> composite = new ArrayList<>(exprs.size());
        for (IEvalAction expr : exprs) {
            composite.add(expr.invoke(XplFunctionSupport.newCallScope()
                    .newChildScope(Collections.singletonMap("event", event))));
        }
        return composite;
    }
}
