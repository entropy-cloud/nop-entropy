/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.xlang;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.truffle.runtime.XLangContextPool;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * 基准用例③：Context 池创建/销毁成本 + 池大小敏感性梯度（I12 Phase 2，I8 移交调优实测）。
 *
 * <ul>
 * <li><b>poolOpenClose</b>：`XLangContextPool.open(size)` + close 全周期（每 Context 创建 +
 * 预热求值 + 关闭）——池创建/销毁成本数据行；{@code @Param} 在缺省
 * `availableProcessors` 邻域扫梯度（1/2/4/8/16）。</li>
 * <li><b>poolLeaseEvalSteady</b>（单线程）与 <b>poolLeaseEvalConcurrent4</b>（4 线程）：
 * 预开池上 租借→求值→归还 稳态吞吐 × 池大小梯度——池 &lt; 线程数时呈现有界阻塞等待
 * （池耗尽语义），池 ≥ 线程数时并行扩展；两形态对比 = 池大小敏感性数据。</li>
 * </ul>
 * 梯度经 {@code XLangContextPool.open(int)} 显式构造参数化——真实运行时路径内、单 JVM
 * 多 run（D2 梯度机制约束），不走配置键多进程。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class ContextPoolBenchmark {

    @Param({"1", "2", "4", "8", "16"})
    int poolSize;

    private XLangContextPool pool;
    private IExecutableExpression tree;

    @Setup(Level.Trial)
    public void setup() {
        CoreInitialization.initialize();
        try {
            tree = DynamicCorpus.units().get(0).compile();
            // 预开池（创建 + 预热全部 Context——open 本身的成本由 poolOpenClose 承载）
            pool = XLangContextPool.open(poolSize);
            try (XLangContextPool.Lease lease = pool.lease()) {
                Object value = lease.eval("bm:pool:steady", tree,
                        DynamicCorpus.units().get(0).newScope(), DisabledEvalOutput.INSTANCE).getReturnValue();
                if (!Integer.valueOf(7).equals(value))
                    throw new IllegalStateException("pool steady-state eval sanity failed: " + value);
            }
        } catch (RuntimeException e) {
            CoreInitialization.destroy();
            throw e;
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (pool != null)
            pool.close();
        CoreInitialization.destroy();
    }

    /** 池创建/销毁全周期成本（open = 每 Context 创建 + 预热求值；close = 全部回收） */
    @Benchmark
    public void poolOpenClose(Blackhole bh) {
        try (XLangContextPool opened = XLangContextPool.open(poolSize)) {
            bh.consume(opened.getMaxSize());
        }
    }

    /** 租借→求值→归还 稳态（单线程） */
    @Benchmark
    public void poolLeaseEvalSteady(Blackhole bh) {
        try (XLangContextPool.Lease lease = pool.lease()) {
            bh.consume(lease.eval("bm:pool:steady", tree,
                    DynamicCorpus.units().get(0).newScope(), DisabledEvalOutput.INSTANCE).getReturnValue());
        }
    }

    /** 租借→求值→归还 稳态（4 线程并发——池 &lt; 4 时有界阻塞等待，≥ 4 时并行） */
    @Benchmark
    @Threads(4)
    public void poolLeaseEvalConcurrent4(Blackhole bh) {
        try (XLangContextPool.Lease lease = pool.lease()) {
            bh.consume(lease.eval("bm:pool:steady4", tree,
                    DynamicCorpus.units().get(0).newScope(), DisabledEvalOutput.INSTANCE).getReturnValue());
        }
    }
}
