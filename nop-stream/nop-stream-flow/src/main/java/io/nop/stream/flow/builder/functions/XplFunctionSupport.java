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
 * <p>Fresh-root discipline (CRA 8.45 R4-05 W-05 / 1.3-C1-002): every invocation
 * gets its OWN root scope (allocated via {@link EvalExprProvider#newEvalScope()}),
 * so there is no JVM-level shared eval state at all — variables bound at invocation
 * time cannot leak across invocations, and even a future caller that mistakenly
 * retains a scope across records can only pollute itself, never the whole JVM.
 * Exceptions thrown by the xpl body propagate to the caller unchanged (no silent
 * swallow).
 */
public final class XplFunctionSupport {

    private XplFunctionSupport() {
    }

    public static IEvalScope newCallScope() {
        return EvalExprProvider.newEvalScope();
    }

    /**
     * WI11 (plan 2357): call scope with static bindings — the declarative side-input
     * snapshots ({@code sideInputs.<id>} access) plus any future build-time-frozen
     * bindings. The map's entries are bound as scope variables; {@code null} bindings
     * behave exactly like {@link #newCallScope()}.
     *
     * <p>The bindings map must be a build-time frozen read-only snapshot (see
     * {@code StreamModelDslBuilder#materializeSideInputs} — CRA 8.45 R4-05 W-01);
     * it is adopted as-is (zero per-record copy) and every write into it fails typed
     * with {@link UnsupportedOperationException}.
     */
    public static IEvalScope newCallScope(java.util.Map<String, Object> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return EvalExprProvider.newEvalScope();
        }
        return EvalExprProvider.newEvalScope().newChildScope(bindings);
    }

    /**
     * Convert the xpl body return value to a {@code boolean}. Used by filter
     * functions ({@code xpl-fn:(event)=>boolean}).
     */
    public static boolean toBoolean(Object value) {
        return ConvertHelper.toTruthy(value);
    }
}
