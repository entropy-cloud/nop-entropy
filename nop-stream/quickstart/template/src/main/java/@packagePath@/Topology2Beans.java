package @package@;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeyedProcessFunction;
import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;

/**
 * 入门拓扑 2 的 bean 集 —— keyBy + 窗口聚合 + keyed state（XDSL 形态，见
 * {@code _vfs/quickstart/topology2-window-aggregation.stream.xml}）。
 *
 * <p>拓扑：tradeSource → tradeWatermarks → keyBy(userId) → sequenceEnricher（keyed
 * ValueState 逐用户计数）→ 2 秒事件时间滚动窗口 → countSumAggregate → 收集 sink。
 */
public final class Topology2Beans {

    public static final long WINDOW_SIZE_MS = 2_000;

    /** 远未来水位泵事件事件时间：把 watermark 推过全部数据窗口使其闭合（泵自身窗口不闭合）。 */
    public static final long PUMP_EVENT_TIME = 50_000;

    private Topology2Beans() {
    }

    /** 有界 source：2 用户 × 2 窗口 × 各 2 笔 + 尾部水位泵。 */
    public static final class TradeSource implements SourceFunction<TradeEvent> {
        private static final long serialVersionUID = 1L;

        private volatile boolean running = true;

        @Override
        public void run(SourceContext<TradeEvent> ctx) {
            List<TradeEvent> events = new ArrayList<>();
            // 事件按事件时间升序发射（两用户交错）。watermarkInterval=0 时逐事件推进水位，
            // 跨 key 乱序到达的数据会被判定迟到（allowedLateness=0 下丢弃）——事件时间有序
            // 是逐事件水位语义下两个用户的首窗口都能正常闭合的前提。
            // user-1 [0,2000)=10/20、[2000,4000)=30/40；user-2 [0,2000)=100/200、[2000,4000)=400/500
            events.add(new TradeEvent(1_000, "user-1", 10.0));
            events.add(new TradeEvent(1_100, "user-2", 100.0));
            events.add(new TradeEvent(1_500, "user-1", 20.0));
            events.add(new TradeEvent(1_600, "user-2", 200.0));
            events.add(new TradeEvent(2_500, "user-1", 30.0));
            events.add(new TradeEvent(2_600, "user-2", 400.0));
            events.add(new TradeEvent(3_500, "user-1", 40.0));
            events.add(new TradeEvent(3_600, "user-2", 500.0));
            // 水位泵（自身处于永不闭合的远未来窗口，不污染断言）
            events.add(new TradeEvent(PUMP_EVENT_TIME, "__pump__", 0.0));

            for (TradeEvent event : events) {
                if (!running) {
                    return;
                }
                ctx.collect(event);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** 事件时间水位策略（有界乱序 100ms + 从事件字段取事件时间）。 */
    public static WatermarkStrategy<TradeEvent> watermarks() {
        return WatermarkStrategy.<TradeEvent>forBoundedOutOfOrderness(Duration.ofMillis(100))
                .withTimestampAssigner((event, recordTimestamp) -> event.getEventTime());
    }

    /** 窗口分配器 bean：2 秒事件时间滚动窗口。 */
    public static TumblingEventTimeWindows windowAssigner() {
        return TumblingEventTimeWindows.of(WINDOW_SIZE_MS);
    }

    /**
     * keyed state 富化算子：每用户逐笔计数写回 {@code seq}（ValueState 随 checkpoint 持久化）。
     *
     * <p>状态采用惰性获取（首次 processElement 时初始化）——keyed store 在算子 open 之后
     * 才注入 RuntimeContext，open() 内直接访问会 fail-fast（与 fraud-example 的
     * {@code UserHistoryEnricher} 同款惯用法）。
     */
    public static final class SequenceEnricher extends KeyedProcessFunction<String, TradeEvent, TradeEvent> {
        private static final long serialVersionUID = 1L;

        private transient ValueState<Integer> userSeq;

        private ValueState<Integer> userSeq() {
            if (userSeq == null) {
                userSeq = getRuntimeContext().getKeyedStateStore()
                        .getState(new ValueStateDescriptor<>("user-seq", Integer.class));
            }
            return userSeq;
        }

        @Override
        public void processElement(TradeEvent event,
                                   ProcessFunction<TradeEvent, TradeEvent>.Context ctx,
                                   Collector<TradeEvent> out) throws Exception {
            Integer current = userSeq().value();
            int next = (current == null ? 0 : current) + 1;
            userSeq().update(next);
            event.setSeq(next);
            out.collect(event);
        }
    }

    /** 窗口聚合输出：每 (用户, 窗口) 一行的计数/总额/末笔序列号（键语义见流经的 keyBy）。 */
    public static final class WindowSummary {
        private final long count;
        private final double totalAmount;
        private final int lastSeq;

        public WindowSummary(long count, double totalAmount, int lastSeq) {
            this.count = count;
            this.totalAmount = totalAmount;
            this.lastSeq = lastSeq;
        }

        @Override
        public String toString() {
            return "count=" + count + "|total=" + totalAmount + "|lastSeq=" + lastSeq;
        }
    }

    /** 窗口聚合函数 bean：计数 + 求和 + 保留末笔 seq（seq 证明 keyed-state 富化已执行）。 */
    public static final class CountSumAggregate
            implements AggregateFunction<TradeEvent, CountSumAggregate.Acc, WindowSummary> {
        private static final long serialVersionUID = 1L;

        public static final class Acc {
            long count;
            double total;
            int lastSeq;
        }

        @Override
        public Acc createAccumulator() {
            return new Acc();
        }

        @Override
        public Acc add(TradeEvent event, Acc acc) {
            acc.count++;
            acc.total += event.getAmount();
            acc.lastSeq = event.getSeq();
            return acc;
        }

        @Override
        public WindowSummary getResult(Acc acc) {
            return new WindowSummary(acc.count, acc.total, acc.lastSeq);
        }

        @Override
        public Acc merge(Acc a, Acc b) {
            Acc merged = new Acc();
            merged.count = a.count + b.count;
            merged.total = a.total + b.total;
            merged.lastSeq = Math.max(a.lastSeq, b.lastSeq);
            return merged;
        }
    }
}
