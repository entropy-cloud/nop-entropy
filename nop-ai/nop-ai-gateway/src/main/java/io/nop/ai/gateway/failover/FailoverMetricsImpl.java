package io.nop.ai.gateway.failover;

import io.nop.ai.core.reliability.CircuitState;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * W8 failover 指标 micrometer 默认实现（plan 2026-08-15-1615-1，OBS-01；nop-job
 * {@code JobWorkerMetricsImpl} 先例模式）：全部指标经 {@link GlobalMeterRegistry#instance()}
 * 暴露（构造可注入任意 {@link MeterRegistry}，测试注入 SimpleMeterRegistry 做增量断言）。
 *
 * <p><b>无空壳</b>：每个接口方法都有真实记录（经 registry 按名 + 维度标签取 meter 后
 * 计数/计时，micrometer 按 meter id 缓存实例）；记录异常捕获必记日志（观测面独立于
 * 控制面——指标异常不外泄为业务错误，契约 §3.6 裁定）。null 维度标签规范化为空串
 * （主账号无 accountKey 等场景）。
 */
public class FailoverMetricsImpl implements IFailoverMetrics {

    static final Logger LOG = LoggerFactory.getLogger(FailoverMetricsImpl.class);

    private final MeterRegistry registry;

    public FailoverMetricsImpl() {
        this(GlobalMeterRegistry.instance());
    }

    public FailoverMetricsImpl(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onSwitchAttempt(String provider, String model, String accountKey) {
        record(() -> registry.counter("nop.ai.gateway.failover.switch.total",
                "provider", nvl(provider), "model", nvl(model), "account", maskAccount(accountKey)).increment());
    }

    @Override
    public void onResubscribe(String provider, String model, String accountKey) {
        record(() -> registry.counter("nop.ai.gateway.failover.resubscribe.total",
                "provider", nvl(provider), "model", nvl(model), "account", maskAccount(accountKey)).increment());
    }

    @Override
    public void onCircuitTransition(String provider, String model, CircuitState toState) {
        record(() -> registry.counter("nop.ai.gateway.failover.circuit-transition.total",
                "provider", nvl(provider), "model", nvl(model), "to-state", toState.name()).increment());
    }

    @Override
    public void onCooldown(String provider, String model, CooldownEventType type) {
        record(() -> registry.counter("nop.ai.gateway.failover.cooldown.total",
                "provider", nvl(provider), "model", nvl(model),
                "type", type.name().toLowerCase()).increment());
    }

    @Override
    public void onRequestSuccess(String provider, String model, String accountKey, long durationMs) {
        record(() -> {
            registry.counter("nop.ai.gateway.failover.request-success.total",
                    "provider", nvl(provider), "model", nvl(model), "account", maskAccount(accountKey)).increment();
            requestTimer(provider, model, accountKey, "success").record(Duration.ofMillis(durationMs));
        });
    }

    @Override
    public void onRequestFailure(String provider, String model, String accountKey, long durationMs) {
        record(() -> {
            registry.counter("nop.ai.gateway.failover.request-failure.total",
                    "provider", nvl(provider), "model", nvl(model), "account", maskAccount(accountKey)).increment();
            requestTimer(provider, model, accountKey, "failure").record(Duration.ofMillis(durationMs));
        });
    }

    @Override
    public void onSaturation(String provider, String modelClass) {
        record(() -> registry.counter("nop.ai.gateway.failover.saturation.total",
                "provider", nvl(provider), "model-class", nvl(modelClass)).increment());
    }

    @Override
    public void onConcurrencyAcquire(String provider, String accountKey) {
        record(() -> registry.counter("nop.ai.gateway.failover.concurrency-acquire.total",
                "provider", nvl(provider), "account", maskAccount(accountKey)).increment());
    }

    @Override
    public void onConcurrencyRelease(String provider, String accountKey) {
        record(() -> registry.counter("nop.ai.gateway.failover.concurrency-release.total",
                "provider", nvl(provider), "account", maskAccount(accountKey)).increment());
    }

    @Override
    public void onTakeover(String provider, String model, String accountKey) {
        record(() -> registry.counter("nop.ai.gateway.failover.takeover.total",
                "provider", nvl(provider), "model", nvl(model), "account", maskAccount(accountKey)).increment());
    }

    @Override
    public void onDegradedTermination(String provider, String model, String accountKey) {
        record(() -> registry.counter("nop.ai.gateway.failover.degraded.total",
                "provider", nvl(provider), "model", nvl(model), "account", maskAccount(accountKey)).increment());
    }

    @Override
    public void onStreamElementConverted(String provider, String model, String accountKey) {
        record(() -> registry.counter("nop.ai.gateway.failover.stream-element.total",
                "provider", nvl(provider), "model", nvl(model),
                "account", maskAccount(accountKey)).increment());
    }

    private Timer requestTimer(String provider, String model, String accountKey, String outcome) {
        return registry.timer("nop.ai.gateway.failover.request.duration",
                "provider", nvl(provider), "model", nvl(model), "account", maskAccount(accountKey),
                "outcome", outcome);
    }

    private static String nvl(String value) {
        return value != null ? value : "";
    }

    /**
     * F-AI4-1：accountKey 是备用账号的直配 apiKey（敏感值），不得作为
     * micrometer 标签原样导出到观测面。掩码保留可区分性（前4位+长度），
     * 不暴露密钥本体。
     */
    static String maskAccount(String accountKey) {
        if (accountKey == null || accountKey.isEmpty())
            return nvl(accountKey);
        if (accountKey.length() <= 4)
            return "***";
        return accountKey.substring(0, 4) + "***(" + accountKey.length() + ")";
    }

    /**
     * 观测面独立于控制面：记录异常捕获并记日志，不外泄为业务错误（契约 §3.6 裁定）。
     */
    private void record(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            LOG.warn("nop.ai.gateway.failover metric recording failed, ignored", t);
        }
    }
}
