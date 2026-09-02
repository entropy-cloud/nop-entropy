package @package@;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.environment.StreamExecutionEnvironment;

/**
 * 入门拓扑 1 —— 最小链路 source → transform → sink（Java DataStream API 形态）。
 *
 * <p>fromElements 有界 source → map（大写）→ filter（B 开头）→ 收集 sink。
 * 预期输出：{@code [BANANA, BLUEBERRY]}。
 *
 * <p>语义组合要点：默认 processingGuarantee 为 STRICT_EXACTLY_ONCE（要求 REPLAYABLE
 * source + 2PC sink）；本拓扑的内联 source/sink 只满足 at-least-once，故显式声明降档——
 * 这也是产品「语义组合规则」的最小示例（build 期校验，违配 fail-fast）。
 */
public final class Topology1MinimalPipeline {

    private Topology1MinimalPipeline() {
    }

    public static List<String> run() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.getCheckpointConfig().setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);

        List<String> output = Collections.synchronizedList(new ArrayList<>());
        env.fromElements("apple", "avocado", "banana", "blueberry", "cherry")
                .map(String::toUpperCase)
                .filter(fruit -> fruit.startsWith("B"))
                .sink(output::add);

        env.execute("topology1-minimal-pipeline");
        return new ArrayList<>(output);
    }

    public static void main(String[] args) throws Exception {
        for (String value : run()) {
            System.out.println("[topology1] " + value);
        }
    }
}
