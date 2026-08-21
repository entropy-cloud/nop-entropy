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
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.backend.EvalBackendDecision;
import io.nop.xlang.backend.EvalBackendRouter;
import io.nop.xlang.backend.EvalStaticBoundExecutable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基准用例②：动态单元对比（I12 Phase 2）——自带语料（{@link DynamicCorpus} 10 单元）
 * × 两列：
 * <ul>
 * <li><b>truffle 列 = 真实生产路径</b>：{@code XLang.execute} choke point → 决策树动态分支
 * → truffle 后端池运行时（租借求值稳态；翻译缓存命中后不再翻译）。setup 期路由身份核验：
 * 最近裁决 RouteKind = DYNAMIC（决策树动态分支→truffle 后端，防静默测错后端）。<b>stock
 * JVM 上 truffle 为解释执行稳态
 * （JIT 生效形态仅在 GraalVM 形态成立——报告数据行按形态标注）</b>。</li>
 * <li><b>interpreter 列 = 显式旁路</b>（动态分支经 choke point 会被路由到 truffle）——
 * 同树直驱全局执行器；setup 期身份核验：树非 bound 包装 + 基线值断言。</li>
 * </ul>
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class DynamicCompareBenchmark {

    private static final class Prepared {
        final DynamicCorpus.Unit unit;
        final IExecutableExpression tree;

        Prepared(DynamicCorpus.Unit unit, IExecutableExpression tree) {
            this.unit = unit;
            this.tree = tree;
        }
    }

    private List<Prepared> prepared;

    @Setup(Level.Trial)
    public void setup() {
        CoreInitialization.initialize();
        try {
            DynamicCorpus.verifyInterpreterBaseline();
            prepared = new ArrayList<>();
            for (DynamicCorpus.Unit unit : DynamicCorpus.units()) {
                IExecutableExpression tree = unit.compile();
                if (tree instanceof EvalStaticBoundExecutable)
                    throw new IllegalStateException("interpreter column tree must not be bound: "
                            + unit.getName());
                prepared.add(new Prepared(unit, tree));
            }

            // truffle 列路由身份核验：choke point 动态裁决 RouteKind = TRUFFLE（真实生产路径）
            Prepared first = prepared.get(0);
            EvalBackendRouter.instance().clearRecentDecisions();
            Object routed = XLang.execute(first.tree, new EvalRuntime(first.unit.newScope(),
                    DisabledEvalOutput.INSTANCE));
            List<EvalBackendDecision> decisions = EvalBackendRouter.instance().getRecentDecisions();
            if (decisions.isEmpty() || decisions.get(decisions.size() - 1).getKind()
                    != EvalBackendDecision.RouteKind.DYNAMIC)
                throw new IllegalStateException("truffle column must route through the choke point to the "
                        + "dynamic backend (truffle), decisions=" + decisions);
            if (!BenchmarkValues.valueEquals(first.unit.expected, routed))
                throw new IllegalStateException("truffle column routed value mismatch: " + first.unit.getName());
        } catch (RuntimeException e) {
            CoreInitialization.destroy();
            throw e;
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        CoreInitialization.destroy();
    }

    @Benchmark
    public void interpreterDynamicCorpus(Blackhole bh) {
        for (Prepared p : prepared) {
            IEvalScope scope = p.unit.newScope();
            bh.consume(EvalExprProvider.getGlobalExecutor().execute(p.tree,
                    new EvalRuntime(scope, DisabledEvalOutput.INSTANCE)));
        }
    }

    @Benchmark
    public void truffleDynamicCorpus(Blackhole bh) {
        for (Prepared p : prepared) {
            IEvalScope scope = p.unit.newScope();
            bh.consume(XLang.execute(p.tree, new EvalRuntime(scope, DisabledEvalOutput.INSTANCE)));
        }
    }
}
