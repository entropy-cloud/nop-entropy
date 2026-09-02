package @package@;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 入门拓扑 1 测试：最小链路 source → transform → sink（Java DataStream API）。
 */
public class Topology1MinimalPipelineTest {

    @Test
    public void minimalPipelineProducesExpectedOutput() throws Exception {
        List<String> output = Topology1MinimalPipeline.run();

        assertEquals(List.of("BANANA", "BLUEBERRY"), output);
    }
}
