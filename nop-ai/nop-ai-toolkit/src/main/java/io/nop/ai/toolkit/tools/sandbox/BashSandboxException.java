package io.nop.ai.toolkit.tools.sandbox;

/**
 * Fail-closed exception raised by a {@link IBashSandbox} backend when it cannot launch, observe,
 * or terminate the requested command, or when the requested working directory / environment is
 * rejected before launch (plan 335 DR-3a).
 *
 * <p><b>Fail-closed contract</b>: a functional backend (e.g. {@link DockerBashSandbox}) that
 * cannot reach its isolation layer MUST raise this exception and MUST NOT silently fall back to
 * host execution. {@link BashExecutor} treats this exception as a hard error result — it never
 * retries on the host shell.
 */
/**
 * 审计 S08 裁定（2026-09-13）：保留 extends RuntimeException——本异常是 BashExecutor 的
 * 工具结果协议（hard-error result 语义，携带 FailureReason 枚举），不进 ErrorCode/GraphQL
 * 错误管道；转 NopException 会破坏 fail-closed 消费协议。
 */
public class BashSandboxException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final BashSandboxFailureReason reason;

    public BashSandboxException(BashSandboxFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public BashSandboxException(BashSandboxFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public BashSandboxFailureReason getReason() {
        return reason;
    }
}
