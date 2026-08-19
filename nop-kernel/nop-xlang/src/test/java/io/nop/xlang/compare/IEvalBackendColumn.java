package io.nop.xlang.compare;

/**
 * 后端列注册 SPI（测试域显式注册表，不做 classpath 扫描）：
 * 后端标识 + 能力声明（适用静态/动态单元）+ 执行入口（返回实际执行工件证据，供 harness 判定身份）。
 */
public interface IEvalBackendColumn {
    String getBackendId();

    boolean isSupportsStaticUnits();

    boolean isSupportsDynamicUnits();

    /**
     * 执行请求中的树实例与求值现场，返回执行结果（含证据）。列不得自证身份。
     */
    BackendExecutionResult execute(BackendExecRequest request);
}
