package io.nop.xlang.compare;

import io.nop.core.lang.eval.EvalExprProvider;

/**
 * java / truffle 列的临时身份规则（I1 阶段：真实后端列尚未落盘）：
 * 执行工件必须不是解释器树本身、驱动工件必须不是全局解释器执行器。
 * I2/I5 落盘时可经 {@code replaceIdentityRule} 用更强规则（生成类实例 / CallTarget 判定）替换本规则。
 */
public final class NonInterpreterArtifactIdentityRule implements IBackendIdentityRule {
    private final String backendId;

    public NonInterpreterArtifactIdentityRule(String backendId) {
        this.backendId = backendId;
    }

    @Override
    public String getBackendId() {
        return backendId;
    }

    @Override
    public void verifyIdentity(BackendExecutionEvidence evidence, BackendExecRequest request) {
        Object executed = evidence.getExecutedArtifact();
        Object executor = evidence.getExecutorArtifact();

        if (executed == request.getExpr()) {
            throw new AssertionError(backendId + " column must not execute the interpreter tree itself: executed="
                    + CompareValues.display(executed) + " (vacuous pass guard)");
        }
        if (executor == EvalExprProvider.getGlobalExecutor()) {
            throw new AssertionError(backendId + " column must not be driven by the interpreter executor: executor="
                    + CompareValues.display(executor));
        }
    }
}
