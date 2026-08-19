package io.nop.xlang.compare;

/**
 * 列执行结果：返回值 / 抛出的异常（异常语义证据）/ 执行工件证据。
 * 后端执行中抛出的异常记录在 {@link #getThrown()}；harness 级错误（如证据缺失）由执行入口直接抛出。
 */
public final class BackendExecutionResult {
    private final Object returnValue;
    private final Throwable thrown;
    private final BackendExecutionEvidence evidence;

    public BackendExecutionResult(Object returnValue, Throwable thrown, BackendExecutionEvidence evidence) {
        if (evidence == null)
            throw new IllegalArgumentException("execution evidence is required for identity assertion");
        this.returnValue = returnValue;
        this.thrown = thrown;
        this.evidence = evidence;
    }

    public static BackendExecutionResult value(Object returnValue, BackendExecutionEvidence evidence) {
        return new BackendExecutionResult(returnValue, null, evidence);
    }

    public static BackendExecutionResult error(Throwable thrown, BackendExecutionEvidence evidence) {
        return new BackendExecutionResult(null, thrown, evidence);
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public Throwable getThrown() {
        return thrown;
    }

    public BackendExecutionEvidence getEvidence() {
        return evidence;
    }
}
