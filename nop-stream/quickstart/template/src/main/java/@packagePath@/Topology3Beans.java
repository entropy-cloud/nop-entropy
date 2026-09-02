package @package@;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.util.Collector;

/**
 * 入门拓扑 3 的 bean 集 —— CEP 简单模式（XDSL 形态，见
 * {@code _vfs/quickstart/topology3-cep-pattern.stream.xml}）。
 *
 * <p>拓扑：cepSource → 水位 → keyBy(userId) → cep(rapid-transactions：30 秒内
 * 严格相邻的两笔 &gt;1000 交易) → 告警 select → 收集 sink。
 * 预期：只有 {@code account-a} 命中（两笔 &gt;1000）；{@code account-b} 不命中。
 */
public final class Topology3Beans {

    private Topology3Beans() {
    }

    /** 有界 source：account-a 两笔大额（命中），account-b 仅一笔大额（不命中），尾部水位泵。 */
    public static final class CepSource implements SourceFunction<TradeEvent> {
        private static final long serialVersionUID = 1L;

        private volatile boolean running = true;

        @Override
        public void run(SourceContext<TradeEvent> ctx) {
            List<TradeEvent> events = new ArrayList<>();
            // account-a：1500 → 1800，两笔均 > 1000，间隔 500ms（30s 内）→ 命中
            events.add(new TradeEvent(1_000, "account-a", 1_500.0));
            events.add(new TradeEvent(1_500, "account-a", 1_800.0));
            // account-b：900 → 1200，首笔不满足 > 1000 → 不命中
            events.add(new TradeEvent(1_000, "account-b", 900.0));
            events.add(new TradeEvent(1_500, "account-b", 1_200.0));
            // 水位泵：推进 watermark 使 30s within 语义上的残留部分匹配超时清理
            events.add(new TradeEvent(Topology2Beans.PUMP_EVENT_TIME, "__pump__", 0.0));

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

    /** 模式命中输出：{@code <userId>:RAPID}。 */
    public static final class RapidAlertSelect extends PatternProcessFunction<TradeEvent, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void processMatch(Map<String, List<TradeEvent>> match, Context ctx, Collector<String> out) {
            TradeEvent first = match.get("first").get(0);
            TradeEvent second = match.get("second").get(0);
            out.collect(first.getUserId() + ":RAPID(" + first.getAmount() + " then " + second.getAmount() + ")");
        }
    }
}
