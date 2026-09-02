package @package@;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 入门拓扑 2 测试：keyBy + 窗口聚合 + keyed state（XDSL 形态）。
 *
 * <p>预期 4 行窗口聚合输出（2 用户 × 2 窗口，每窗 2 笔；lastSeq 证明 keyed-state
 * 富化算子在窗口之前执行）：
 * <pre>
 *   user-1 [0,2000)    : count=2 total=30.0  lastSeq=2
 *   user-1 [2000,4000) : count=2 total=70.0  lastSeq=4
 *   user-2 [0,2000)    : count=2 total=300.0 lastSeq=2
 *   user-2 [2000,4000) : count=2 total=900.0 lastSeq=4
 * </pre>
 */
public class Topology2WindowAggregationTest {

    private static final List<Object> collected = Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    public static void init() {
        QuickstartSupport.initVfs();
        QuickstartSupport.registerCheckpointExecutorFactory();
    }

    @AfterAll
    public static void destroy() {
        QuickstartSupport.unregisterCheckpointExecutorFactory();
        QuickstartSupport.destroyVfs();
    }

    @Test
    public void windowAggregationProducesExpectedRows() throws Exception {
        InMemoryBeanFunctionResolver beans = new InMemoryBeanFunctionResolver()
                .register("tradeSource", new Topology2Beans.TradeSource())
                .register("tradeWatermarks", Topology2Beans.watermarks())
                .register("tradeWindowAssigner", Topology2Beans.windowAssigner())
                .register("sequenceEnricher", new Topology2Beans.SequenceEnricher())
                .register("countSumAggregate", new Topology2Beans.CountSumAggregate())
                .register("resultSink", (io.nop.stream.core.common.functions.SinkFunction<Object>) collected::add);

        StreamExecutionEnvironment env = QuickstartSupport.buildFromXdsl(
                "/quickstart/topology2-window-aggregation.stream.xml", beans);
        // keyed state 需要 IStateBackend（ProcessOperator/WindowOperator/CepOperator
        // 均从 stateBackend 构建 keyed 后端；不设置则 keyed 算子 fail-fast）
        env.getCheckpointConfig().setStateBackend(new MemoryStateBackend());
        env.execute("topology2-window-aggregation");

        Set<String> rows = new TreeSet<>();
        for (Object row : collected) {
            rows.add(row.toString());
        }
        // 第 5 行是尾部水位泵事件自身的窗口：有界运行结束时 TimestampsAndWatermarksOperator
        // 发射 MAX_WATERMARK，全部 in-flight 窗口（含泵所在的远未来窗口）随之闭合。
        assertEquals(Set.of(
                "count=2|total=30.0|lastSeq=2",
                "count=2|total=70.0|lastSeq=4",
                "count=2|total=300.0|lastSeq=2",
                "count=2|total=900.0|lastSeq=4",
                "count=1|total=0.0|lastSeq=1"), rows);
    }
}
