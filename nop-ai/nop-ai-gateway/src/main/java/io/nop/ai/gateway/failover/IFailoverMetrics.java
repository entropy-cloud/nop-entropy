package io.nop.ai.gateway.failover;

import io.nop.ai.core.reliability.CircuitState;

/**
 * W8 failover 可观测性指标服务接口（plan 2026-08-15-1615-1，OBS-01；契约见
 * {@code ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md} §3.6）。
 *
 * <p>实现 = micrometer 默认实现 {@link FailoverMetricsImpl}（nop-job
 * {@code IJobWorkerMetrics} 先例模式），bean {@code nopAiFailoverMetrics}
 * （{@code ioc:default="true"}）注册于 {@code ai-gateway-defaults.beans.xml}，
 * 缺省启用、零行为影响（观测面独立于控制面：指标异常不外泄为业务错误，捕获必记日志）。
 *
 * <p><b>null-object / fail-fast 边界（Phase 1 裁定）</b>：接口可注入 null
 * （null = 观测 no-op，仅测试/手工构造形态；标准部署经 beans.xml 非 optional ref
 * 在容器启动期 fail-fast），但接口方法本身不得空实现——所有方法由真实实现记录指标。
 */
public interface IFailoverMetrics {

    /** 失败后重发至新候选（账号切换）次数；维度 = 新候选 provider/model/account。 */
    void onSwitchAttempt(String provider, String model, String accountKey);

    /** 流式重订阅次数（含 CACHE_STATE_LOST 同候选原地重发）。 */
    void onResubscribe(String provider, String model, String accountKey);

    /**
     * 熔断器状态迁移次数。
     *
     * <p><b>近似观测（契约语义）</b>：{@code ThresholdBreaker.getState} 为无锁读，
     * 并发交错下可能对同一次迁移重复计数或错误归因——禁止把精确语义写进本指标。
     */
    void onCircuitTransition(String provider, String model, CircuitState toState);

    /** 冷却期事件（{@code started} / {@code rejected} / {@code probe-rejected}）。 */
    void onCooldown(String provider, String model, CooldownEventType type);

    /** attempt 成功（durationMs = attempt 耗时，毫秒）。 */
    void onRequestSuccess(String provider, String model, String accountKey, long durationMs);

    /** attempt 失败（durationMs = attempt 耗时，毫秒；不含调用方取消）。 */
    void onRequestFailure(String provider, String model, String accountKey, long durationMs);

    /** 全池饱和 fail-loud（需求 §3.3 饱和指标）。 */
    void onSaturation(String provider, String modelClass);

    /** 并发计数 acquire（配对路径观测，与 {@link #onConcurrencyRelease} 跨指标比对）。 */
    void onConcurrencyAcquire(String provider, String accountKey);

    /** 并发计数 release（配对路径观测）。 */
    void onConcurrencyRelease(String provider, String accountKey);

    /** 网关拦截器接管请求（流式路径首次候选下沉）。 */
    void onTakeover(String provider, String model, String accountKey);

    /** 流式 NON_TRANSIENT 降级终止响应。 */
    void onDegradedTermination(String provider, String model, String accountKey);

    /** onStreamElement per-attempt 反向转换元素数。 */
    void onStreamElementConverted(String provider, String model, String accountKey);
}
