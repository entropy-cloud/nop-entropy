package io.nop.rpc.core.flowcontrol;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.rpc.api.flowcontrol.FlowControlEntry;
import io.nop.rpc.api.flowcontrol.IFlowControlRunner;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static io.nop.rpc.core.RpcErrors.ERR_RPC_FLOW_CONTROL_REJECT;

public class DefaultFlowControlRunner implements IFlowControlRunner {
    private IRateLimiter rateLimiter;

    private int maxWaitMillis = 0;

    public DefaultFlowControlRunner() {
    }

    public DefaultFlowControlRunner(double maxPermitsPerSecond) {
        setMaxPermitsPerSecond(maxPermitsPerSecond);
    }

    public void setMaxPermitsPerSecond(double maxPermitsPerSecond) {
        this.rateLimiter = DefaultRateLimiter.create(maxPermitsPerSecond);
    }

    public void setMaxWaitMillis(int maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
    }

    @Override
    public <T> CompletionStage<T> runAsync(FlowControlEntry entry, Supplier<CompletionStage<T>> task) {
        // 尝试在指定时间内获取permits
        boolean acquired = rateLimiter.tryAcquire(1, maxWaitMillis);

        if (acquired) {
            // 如果成功获取permits，则执行任务
            return task.get();
        } else {
            return FutureHelper.reject(new NopException(ERR_RPC_FLOW_CONTROL_REJECT));
        }
    }

    @Override
    public <T> T run(FlowControlEntry entry, Supplier<T> task) {
        boolean acquired = rateLimiter.tryAcquire(1, maxWaitMillis);

        if (acquired) {
            // 如果成功获取permits，则执行任务
            return task.get();
        } else {
            throw new NopException(ERR_RPC_FLOW_CONTROL_REJECT);
        }
    }
}