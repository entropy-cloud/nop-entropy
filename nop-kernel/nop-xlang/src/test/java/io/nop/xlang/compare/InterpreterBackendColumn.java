package io.nop.xlang.compare;

import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;

/**
 * 解释器基线列：同一棵 Executable 树实例经全局 IExpressionExecutor（DefaultExpressionExecutor）执行。
 * 证据 = {executedArtifact: 树实例, executorArtifact: 全局执行器}，由 harness 依据证据判定身份。
 */
public final class InterpreterBackendColumn implements IEvalBackendColumn {
    public static final InterpreterBackendColumn INSTANCE = new InterpreterBackendColumn();

    private InterpreterBackendColumn() {
    }

    @Override
    public String getBackendId() {
        return CompareBackendIds.INTERPRETER;
    }

    @Override
    public boolean isSupportsStaticUnits() {
        return true;
    }

    @Override
    public boolean isSupportsDynamicUnits() {
        return true;
    }

    @Override
    public BackendExecutionResult execute(BackendExecRequest request) {
        IExecutableExpression expr = request.getExpr();
        EvalRuntime rt = new EvalRuntime(request.getScope(), request.getOut());
        Object executorArtifact = EvalExprProvider.getGlobalExecutor();
        try {
            Object result = EvalExprProvider.getGlobalExecutor().execute(expr, rt);
            return BackendExecutionResult.value(result,
                    new BackendExecutionEvidence(expr, executorArtifact));
        } catch (Throwable e) {
            return BackendExecutionResult.error(e, new BackendExecutionEvidence(expr, executorArtifact));
        }
    }
}
