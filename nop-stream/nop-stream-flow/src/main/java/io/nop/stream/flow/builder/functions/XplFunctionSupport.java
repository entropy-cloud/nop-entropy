/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder.functions;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

/**
 * Shared helpers used by the {@code Xpl*Function} wrappers that adapt a parsed
 * XDSL {@code <source>xpl</source>} body (an {@link IEvalFunction}) to the
 * corresponding {@code io.nop.stream.core} function interface.
 *
 * <p>Each invocation gets a fresh child scope derived from a shared root scope
 * so that {@code event}/{@code out}/{@code ctx} bindings do not leak across
 * invocations. Exceptions thrown by the xpl body propagate to the caller
 * unchanged (no silent swallow).
 */
public final class XplFunctionSupport {

    private XplFunctionSupport() {
    }

    /**
     * A root scope retained across invocations. {@link IEvalFunction} bodies compiled
     * from xpl may capture state (e.g. compiled closures) but variables bound at
     * invocation time must live in per-call child scopes.
     */
    private static final IEvalScope ROOT_SCOPE = EvalExprProvider.newEvalScope();

    public static IEvalScope newCallScope() {
        return ROOT_SCOPE.newChildScope();
    }

    /**
     * WI11 (plan 2357): call scope with static bindings — the declarative side-input
     * snapshots ({@code sideInputs.<id>} access) plus any future build-time-frozen
     * bindings. The map's entries are bound as scope variables; {@code null} bindings
     * behave exactly like {@link #newCallScope()}.
     */
    public static IEvalScope newCallScope(java.util.Map<String, Object> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return ROOT_SCOPE.newChildScope();
        }
        return ROOT_SCOPE.newChildScope(bindings);
    }

    /**
     * Convert the xpl body return value to a {@code boolean}. Used by filter
     * functions ({@code xpl-fn:(event)=>boolean}).
     */
    public static boolean toBoolean(Object value) {
        return ConvertHelper.toTruthy(value);
    }
}
