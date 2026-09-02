package @package@;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 入门拓扑 3 测试：CEP 简单模式（XDSL 形态）。
 *
 * <p>预期只有 account-a 命中 rapid-transactions（两笔 &gt;1000 严格相邻、30 秒内）；
 * account-b 首笔 900 不满足，不命中。
 */
public class Topology3CepPatternTest {

    private static final List<String> alerts = Collections.synchronizedList(new ArrayList<>());

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
    public void cepPatternMatchesOnlyEligibleAccount() throws Exception {
        InMemoryBeanFunctionResolver beans = new InMemoryBeanFunctionResolver()
                .register("cepSource", new Topology3Beans.CepSource())
                .register("tradeWatermarks", Topology2Beans.watermarks())
                .register("rapidAlertSelect", new Topology3Beans.RapidAlertSelect())
                .register("alertSink", (io.nop.stream.core.common.functions.SinkFunction<String>) alerts::add);

        StreamExecutionEnvironment env = QuickstartSupport.buildFromXdsl(
                "/quickstart/topology3-cep-pattern.stream.xml", beans);
        // CEP NFA/SharedBuffer 状态同样从 IStateBackend 构建 keyed 后端
        env.getCheckpointConfig().setStateBackend(new MemoryStateBackend());
        env.execute("topology3-cep-pattern");

        assertEquals(List.of("account-a:RAPID(1500.0 then 1800.0)"), alerts);
    }
}
