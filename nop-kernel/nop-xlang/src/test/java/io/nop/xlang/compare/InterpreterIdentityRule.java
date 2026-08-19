package io.nop.xlang.compare;

import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IExpressionExecutor;

/**
 * 解释器列身份规则：执行证据 = 同一棵 Executable 树实例经全局 IExpressionExecutor 执行。
 * 测试专用注入列可携带本规则的非默认实例（backendId 为注入列自身标识），使其身份可被
 * harness 依据证据正常断言（注入列不伪装身份，只在结果层制造分歧）。
 */
public final class InterpreterIdentityRule implements IBackendIdentityRule {
    public static final InterpreterIdentityRule INSTANCE = new InterpreterIdentityRule(CompareBackendIds.INTERPRETER);

    private final String backendId;

    public InterpreterIdentityRule(String backendId) {
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

        if (request.getExpr() != executed) {
            throw new AssertionError("interpreter column must execute the same executable tree instance: executed="
                    + CompareValues.display(executed) + ", request=" + CompareValues.display(request.getExpr()));
        }
        if (!(executor instanceof IExpressionExecutor) || executor != EvalExprProvider.getGlobalExecutor()) {
            throw new AssertionError("interpreter column must be driven by the global IExpressionExecutor, but executor="
                    + CompareValues.display(executor));
        }
    }
}
