/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * plan 2517 R2-14 裁定⑥（C2-004）：nop-orm 模块内世代 fence（设计权威 §3.4，C1-027 SSOT——R4-18 消费不重建）。
 *
 * <p>唯一回填公式：仅当 {@code readGen == fenceGen} 才回填——实现 = fence.compareAndSet(readGen, readGen+1)，
 * CAS 胜者执行 put（值携带 valueGen=readGen），败者（evict/他方回填已推进世代）放弃回填。判据只有相等性，
 * 无序数比较（不使用「低于/不等即放弃」表述）。</p>
 *
 * <p>fence 生命周期：entry 随缓存 key 活跃度存续——value 弱可达且无在答回填时 GC 可回收（WeakReference 形态）；
 * 新建 fence 取 per-persister 单调分配器（epoch.incrementAndGet() * 1_000_000），使任何重建 entry 的初始世代
 * 严格大于全部历史已发放世代，消除「fence 身份重置 × gen-0 快照碰撞」放行旧世代回填的破口（R2 终轮 Major 修正）。
 * 容量上界兜底：超过 100_000 entry 时整体清空并 WARN 一次（清空即全量「首触」，语义安全，只损并发回填去重精度）。</p>
 *
 * <p>零配置键、零持久化、随 persister 生命周期消亡；不触碰 nop-commons ICache/LocalCache（裁定⑥被拒路径）。</p>
 */
class GenerationFence {
    static final Logger LOG = LoggerFactory.getLogger(GenerationFence.class);

    private static final int CAP = 100_000;

    private final ConcurrentHashMap<String, WeakReference<AtomicLong>> fences = new ConcurrentHashMap<>();
    private final AtomicLong alloc = new AtomicLong();
    private volatile boolean capWarned;

    private AtomicLong newFence() {
        // 非零单调起点：严格大于全部历史已发放世代
        return new AtomicLong(alloc.incrementAndGet() * 1_000_000L);
    }

    AtomicLong fenceFor(String cacheKey) {
        if (fences.size() > CAP) {
            fences.clear();
            if (!capWarned) {
                capWarned = true;
                LOG.warn("nop.orm.gen-fence-cap-cleared:cap={}", CAP);
            }
        }
        WeakReference<AtomicLong> ref = fences.computeIfAbsent(cacheKey, k -> new WeakReference<>(newFence()));
        AtomicLong fence = ref.get();
        if (fence == null) {
            fence = newFence();
            fences.put(cacheKey, new WeakReference<>(fence));
        }
        return fence;
    }

    /**
     * readGen：load 发起时的世代快照
     */
    long snapshot(String cacheKey) {
        return fenceFor(cacheKey).get();
    }

    /**
     * 唯一回填公式：仅当 readGen == fenceGen 才回填（CAS readGen → readGen+1，胜者回填）
     */
    boolean tryAcquireRepair(String cacheKey, long readGen) {
        return fenceFor(cacheKey).compareAndSet(readGen, readGen + 1);
    }

    /**
     * evict 点：remove 语义 + fence incrementAndGet 记失效世代——evict 后任何持有旧 readGen 的迟到回填 CAS 必败
     */
    void invalidate(String cacheKey) {
        fenceFor(cacheKey).incrementAndGet();
    }
}
