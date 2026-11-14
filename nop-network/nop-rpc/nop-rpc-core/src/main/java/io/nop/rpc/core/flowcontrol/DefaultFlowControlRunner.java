package io.nop.rpc.core.flowcontrol;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.rpc.api.flowcontrol.FlowControlEntry;
import io.nop.rpc.api.flowcontrol.IFlowControlRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static io.nop.rpc.core.RpcErrors.ERR_RPC_FLOW_CONTROL_REJECT;

/**
 * 流控 runner（2.3-C8-008，plan 2551 裁定 I）：按 {@code resourceType:resource} 分桶——
 * 单一全局桶曾使 invoker/fetcher 双消耗站点共享同一 QPS 桶（1+K 许可稀释：一个站点的
 * 消耗直接挤占另一站点的配额）。限流原语零换型（维持 IRateLimiter QPS 语义；entry 无
 * 并发上界字段，加字段须动 nop-rpc-api=PA，拒绝），每桶以 runner 配置的
 * maxPermitsPerSecond 懒创建（仓内先例 AiRateLimitGatewayInterceptor.buckets）；
 * {@link #setMaxPermitsPerSecond} 置新值时清空重建全部桶。
 */
public class DefaultFlowControlRunner implements IFlowControlRunner {
    private volatile IRateLimiter rateLimiter;

    private int maxWaitMillis = 0;

    /**
     * 2.3-C8-008：resource 维度分桶（resourceType:resource → IRateLimiter），懒创建。
     * plan 2572 Phase 3：{@link LocalCache}（Caffeine）maximumSize 上限 + expireAfterAccess 淘汰
     * （对齐 gateway 先例 AiRateLimitGatewayInterceptor.buckets 的有界形态——原 ConcurrentHashMap
     * 懒创建无淘汰，键空间为外部输入驱动的 resource 组合，长期运行可无界增长）。
     * TTL 取 1 小时（远大于限流语义的桶静置窗口：空闲桶令牌已回满，驱逐后重建语义无损）。
     */
    private final LocalCache<String, IRateLimiter> buckets = LocalCache.newCache(
            "rpc-flow-control-buckets",
            CacheConfig.newConfig(1000).expireAfterAccess(java.time.Duration.ofHours(1)));

    public DefaultFlowControlRunner() {
    }

    public DefaultFlowControlRunner(double maxPermitsPerSecond) {
        setMaxPermitsPerSecond(maxPermitsPerSecond);
    }

    public void setMaxPermitsPerSecond(double maxPermitsPerSecond) {
        this.rateLimiter = DefaultRateLimiter.create(maxPermitsPerSecond);
        // 分桶版本创建于消耗时点——置新速率后清空旧桶（桶内限速器取自新配置）
        this.buckets.clear();
    }

    public void setMaxWaitMillis(int maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
    }

    /**
     * 2.3-C8-008：entry 维度分桶键。rateLimiter 未配置（老形态直接 setMaxWaitMillis 的裸构造）
     * 时退化为单桶共享限速器（保持构造兼容，null 语义与旧一致）。
     */
    private IRateLimiter bucketFor(FlowControlEntry entry) {
        IRateLimiter global = rateLimiter;
        if (global == null) {
            return null;
        }
        String key = entry.getResourceType() + ":" + entry.getResource();
        return buckets.computeIfAbsent(key, k -> DefaultRateLimiter.create(global.getPermitsPerSecond()));
    }

    @Override
    public <T> CompletionStage<T> runAsync(FlowControlEntry entry, Supplier<CompletionStage<T>> task) {
        IRateLimiter bucket = bucketFor(entry);
        if (bucket == null) {
            // 未配置限速 = 不设流控档（显式语义：直接执行，非静默拒绝）
            return task.get();
        }
        // 尝试在指定时间内获取permits
        boolean acquired = bucket.tryAcquire(1, maxWaitMillis);

        if (acquired) {
            // 如果成功获取permits，则执行任务
            return task.get();
        } else {
            return FutureHelper.reject(new NopException(ERR_RPC_FLOW_CONTROL_REJECT));
        }
    }

    @Override
    public <T> T run(FlowControlEntry entry, Supplier<T> task) {
        IRateLimiter bucket = bucketFor(entry);
        if (bucket == null) {
            return task.get();
        }
        boolean acquired = bucket.tryAcquire(1, maxWaitMillis);

        if (acquired) {
            // 如果成功获取permits，则执行任务
            return task.get();
        } else {
            throw new NopException(ERR_RPC_FLOW_CONTROL_REJECT);
        }
    }
}
