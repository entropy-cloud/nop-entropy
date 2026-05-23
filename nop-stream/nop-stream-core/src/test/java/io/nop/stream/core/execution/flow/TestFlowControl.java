package io.nop.stream.core.execution.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestEdgeConfig {

    @Test
    void testDefaultConfig() {
        EdgeConfig config = EdgeConfig.defaultConfig();
        assertEquals(FlowControlPolicy.BLOCKING_QUEUE, config.getFlowControlPolicy());
        assertEquals(1024, config.getQueueCapacity());
    }
}

class TestMemoryBudget {

    @Test
    void testDefaultLocalBudget() {
        MemoryBudget budget = MemoryBudget.defaultLocalBudget(1000);
        assertEquals(500, budget.getAllocation("stateBackend"));
        assertEquals(300, budget.getAllocation("edgeQueues"));
        assertEquals(200, budget.getAllocation("networkBuffers"));
    }
}
