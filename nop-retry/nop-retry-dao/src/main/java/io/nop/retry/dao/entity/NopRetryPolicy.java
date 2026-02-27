package io.nop.retry.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.retry.dao.entity._gen._NopRetryPolicy;

import static io.nop.retry.dao.NopRetryDaoConstants.*;
import static io.nop.retry.api.NopRetryApiConstants.*;

@BizObjName("NopRetryPolicy")
public class NopRetryPolicy extends _NopRetryPolicy {

    // ==================== 帮助方法：获取配置值（带默认值） ====================

    public int getMaxRetryCountOrDefault() {
        return getMaxRetryCount() != null ? getMaxRetryCount() : DEFAULT_MAX_RETRY_COUNT;
    }

    public int getImmediateRetryCountOrDefault() {
        return getImmediateRetryCount() != null ? getImmediateRetryCount() : DEFAULT_IMMEDIATE_RETRY_COUNT;
    }

    public long getImmediateRetryIntervalMsOrDefault() {
        return getImmediateRetryIntervalMs() != null ? getImmediateRetryIntervalMs() : DEFAULT_IMMEDIATE_RETRY_INTERVAL_MS;
    }

    public int getBackoffStrategyOrDefault() {
        return getBackoffStrategy() != null ? getBackoffStrategy() : BACKOFF_STRATEGY_EXPONENTIAL_BACKOFF;
    }

    public long getInitialIntervalMsOrDefault() {
        return getInitialIntervalMs() != null ? getInitialIntervalMs() : DEFAULT_INITIAL_INTERVAL_MS;
    }

    public long getMaxIntervalMsOrDefault() {
        return getMaxIntervalMs() != null ? getMaxIntervalMs() : DEFAULT_MAX_INTERVAL_MS;
    }

    public double getJitterRatioOrDefault() {
        return getJitterRatio() != null ? getJitterRatio() : DEFAULT_JITTER_RATIO;
    }

    public long getExecutionTimeoutMsOrDefault() {
        return getExecutionTimeoutSeconds() != null 
            ? getExecutionTimeoutSeconds() * 1000L 
            : DEFAULT_EXECUTION_TIMEOUT_SECONDS * 1000L;
    }

    public long getDeadlineTimeoutMsOrDefault() {
        return getDeadlineTimeoutMs() != null ? getDeadlineTimeoutMs() : DEFAULT_DEADLINE_TIMEOUT_MS;
    }

    public int getBlockStrategyOrDefault() {
        return getBlockStrategy() != null ? getBlockStrategy() : BLOCK_STRATEGY_PARALLEL;
    }

    public boolean isCallbackEnabled() {
        return BOOL_YES.equals(getCallbackEnabled());
    }

    public int getCallbackTriggerTypeOrDefault() {
        return getCallbackTriggerType() != null ? getCallbackTriggerType() : CALLBACK_TRIGGER_TYPE_ALWAYS;
    }

    public boolean hasCallbackPolicy() {
        String policyId = getCallbackPolicyId();
        return isCallbackEnabled() && policyId != null && !policyId.isEmpty();
    }

    public long getRetryingTimeoutMsOrDefault() {
        Long value = getRetryingTimeoutMs();
        return value != null ? value : DEFAULT_RETRYING_TIMEOUT_MS;
    }
}
