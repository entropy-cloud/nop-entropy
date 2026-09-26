/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.commons.tuple.Tuple2;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.functions.RuntimeContext;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * NFA 事件处理基准（Plan 360 Phase 1）。
 *
 * <p>构造方式照抄 nop-stream-cep 的 {@code TestNFA}：
 * {@code NFACompiler.compileFactory(pattern, false).createNFA()} +
 * {@code nfa.open(ctx, null)}，配 {@code SharedBuffer}（Memory 侧
 * {@code SimpleKeyedStateStore}）与 {@code nfa.createInitialNFAState()}。
 *
 * <p>负载：深度为 {@code @Param patternDepth}（1/5/20）的
 * {@code begin("a").where(...).followedBy("b")...} 链式模式，每级条件相同；
 * 事件流的 50% 能匹配前缀条件（{@code seq & 1 == 0}），事件时间 1ms 步进。
 * 每 invocation 照 {@code TestNFA.feedEvents} 的调用形态喂入一个事件：
 * {@code advanceTime}（按 within 窗口修剪超时部分匹配、释放 SharedBuffer 条目）+
 * {@code process}，之后复位 {@code NFAState} 变更标志；Blackhole 消费匹配结果数量。
 *
 * <p>{@code within(Duration.ofMillis(100))} 使活跃部分匹配窗口有界——这是
 * followedBy 松散连续性下深度模式不被组合爆炸拖垮的标准做法，同时让
 * advanceTime 的修剪路径进入被测负载。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class NfaProcessBench {
    /** key-scoped accessor 的键（与生产 CepOperator.getCurrentKey() 对应，plan 360 R3）。 */
    private static final Object BENCH_KEY = "bench-key-1";


    private static final long WITHIN_WINDOW_MS = 100L;

    @Param({"1", "5", "20"})
    int patternDepth;

    private NFA<BenchCepEvent> nfa;
    private SharedBuffer<BenchCepEvent> buffer;
    private NFAState state;
    private final AfterMatchSkipStrategy skipStrategy = AfterMatchSkipStrategy.noSkip();

    private long seqCursor;
    private long tsCursor;

    @Setup(Level.Trial)
    public void setup() {
        nfa = NFACompiler.compileFactory(buildPattern(patternDepth), false).createNFA();
        nfa.open(new BenchRuntimeContext(), null);
        buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        state = nfa.createInitialNFAState();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (nfa != null) {
            nfa.close();
        }
    }

    /** 喂入一个事件：advanceTime 修剪 + process 推进 NFA 状态，产出匹配集合。 */
    @Benchmark
    public void processEvent(Blackhole bh) throws Exception {
        long ts = ++tsCursor;
        BenchCepEvent event = new BenchCepEvent(++seqCursor, (seqCursor & 1) == 0);
        try (SharedBufferAccessor<BenchCepEvent> accessor = buffer.getAccessor(BENCH_KEY)) { // key-scoped：与生产 CepOperator 一致（plan 360 R3）
            Tuple2<Collection<Map<String, List<BenchCepEvent>>>,
                    Collection<Tuple2<Map<String, List<BenchCepEvent>>, Long>>> pending =
                    nfa.advanceTime(accessor, state, ts, skipStrategy);
            Collection<Map<String, List<BenchCepEvent>>> matches =
                    nfa.process(accessor, state, event, ts, skipStrategy, null);
            matches.addAll(pending.f0);
            bh.consume(matches.size());
        }
        if (state.isStateChanged()) {
            state.resetStateChanged();
            state.resetNewStartPartialMatch();
        }
    }

    private Pattern<BenchCepEvent, ?> buildPattern(int depth) {
        Pattern<BenchCepEvent, ?> pattern = Pattern.<BenchCepEvent>begin("a")
                .where(SimpleCondition.of(BenchCepEvent::isMatching));
        for (int i = 2; i <= depth; i++) {
            pattern = pattern.followedBy("s" + i)
                    .where(SimpleCondition.of(BenchCepEvent::isMatching));
        }
        return pattern.within(Duration.ofMillis(WITHIN_WINDOW_MS));
    }

    /** 照抄 cep 测试 MockRuntimeContext 的最小实现。 */
    static final class BenchRuntimeContext implements RuntimeContext {
        @Override
        public int getIndexOfThisSubtask() {
            return 0;
        }

        @Override
        public int getNumberOfParallelSubtasks() {
            return 1;
        }

        @Override
        public String getTaskName() {
            return "bench-task";
        }
    }
}
