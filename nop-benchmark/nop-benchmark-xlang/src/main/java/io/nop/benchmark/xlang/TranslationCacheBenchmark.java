/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.xlang;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.truffle.translate.TranslatedUnit;
import io.nop.xlang.truffle.translate.TranslationCache;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基准用例④：翻译缓存容量敏感性（I12 Phase 2，I8 移交调优实测 + Q4 前置条件数据）。
 *
 * <p><b>隔离基准形态</b>（D2 梯度机制约束——live 事实：`XLangLanguage` 内 cache 为无参构造
 * 私有字段、无运行时注入缝；容量梯度经 `TranslationCache(Integer)` 显式构造直驱）：
 * <ul>
 * <li><b>cacheSweep64</b>：固定工作集 64 棵互异树逐键 `getOrBuild`（"bm:cache:i"）——
 * 容量 ≥ 64 → 全命中态（命中成本）；容量 &lt; 64 → 持续 LRU 淘汰 + 再翻译态（ miss 成本）。
 * {@code @Param} 扫现缺省 1024 邻域（16/64/256/1024/4096）——命中/未命中差值 = 缓存收益
 * （Q4 编译线程预算口径数据行）。</li>
 * <li><b>translateUnitCost</b>：全 miss 直译（逐次新键）——翻译单成本数据行（Q1/Q4 锚点）。
 * 独立大容量缓存（65536）排除淘汰噪声。</li>
 * </ul>
 * 注：隔离基准的 language = null（测试直构路径先例——纯翻译，无观测载体；语义与 in-situ
 * 相同，in-situ 形态如需可经配置键构造独立实例顺序 run，本报告未启用）。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class TranslationCacheBenchmark {

    /** 固定工作集大小（互异树数） */
    static final int WORKLOAD = 64;

    @Param({"16", "64", "256", "1024", "4096"})
    int maxEntries;

    private TranslationCache cache;
    private TranslationCache missCache;
    private List<IExecutableExpression> workload;
    private final AtomicLong missSeq = new AtomicLong();

    @Setup(Level.Trial)
    public void setup() {
        CoreInitialization.initialize();
        try {
            workload = DynamicCorpus.translationWorkload(WORKLOAD);
            cache = new TranslationCache(maxEntries);
            missCache = new TranslationCache(65536);
            // 基线健全性：首翻译可执行 + 命中路径返回同一实例
            TranslatedUnit first = cache.getOrBuild(key(0), workload.get(0), null);
            if (first == null || first.getRootNode() == null)
                throw new IllegalStateException("translation must produce a root node");
            if (cache.getOrBuild(key(0), workload.get(0), null) != first)
                throw new IllegalStateException("cache hit must return the same translated unit");
        } catch (RuntimeException e) {
            CoreInitialization.destroy();
            throw e;
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        CoreInitialization.destroy();
    }

    private static String key(int i) {
        return "bm:cache:" + i;
    }

    /** 64 键全工作集扫描：容量 ≥ 64 命中态 / &lt; 64 淘汰再翻译态 */
    @Benchmark
    public void cacheSweep64(Blackhole bh) {
        for (int i = 0; i < WORKLOAD; i++)
            bh.consume(cache.getOrBuild(key(i), workload.get(i), null));
    }

    /** 翻译单成本（全 miss：逐次新键直译，大容量缓存排除淘汰噪声） */
    @Benchmark
    public void translateUnitCost(Blackhole bh) {
        int i = (int) (missSeq.incrementAndGet() % WORKLOAD);
        bh.consume(missCache.getOrBuild("bm:miss:" + missSeq.incrementAndGet(), workload.get(i), null));
    }
}
