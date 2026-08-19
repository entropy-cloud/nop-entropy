package io.nop.xlang.compare;

/**
 * 后端身份判定规则：harness 依据执行证据对照列声明的后端标识断言。
 * 规则按后端标识注册，与列分离（列不得自带身份判定）。某后端标识无已注册规则时，
 * 身份维度无法断言，该列显式判 FAIL（不算通过）。
 */
public interface IBackendIdentityRule {
    String getBackendId();

    /**
     * 校验证据与声明的后端标识一致；不一致时抛出 {@link AssertionError}（含差异说明）。
     */
    void verifyIdentity(BackendExecutionEvidence evidence, BackendExecRequest request);
}
