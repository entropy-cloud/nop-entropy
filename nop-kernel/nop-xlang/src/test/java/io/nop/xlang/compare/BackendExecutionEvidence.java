package io.nop.xlang.compare;

/**
 * 列执行入口返回的实际执行工件证据。列不得自证身份：身份判定由 harness 依据本证据
 * 对照该列声明的后端标识做出（设计 execution 01 §五：不断言身份的比对判无效）。
 *
 * <p>executedArtifact = 实际被执行的工件（解释器列 = Executable 树实例；
 * java 列 = 生成类实例；truffle 列 = 翻译 AST/CallTarget）。
 * executorArtifact = 实际驱动执行的工件（解释器列 = IExpressionExecutor）。
 */
public final class BackendExecutionEvidence {
    private final Object executedArtifact;
    private final Object executorArtifact;

    public BackendExecutionEvidence(Object executedArtifact, Object executorArtifact) {
        this.executedArtifact = executedArtifact;
        this.executorArtifact = executorArtifact;
    }

    public Object getExecutedArtifact() {
        return executedArtifact;
    }

    public Object getExecutorArtifact() {
        return executorArtifact;
    }
}
