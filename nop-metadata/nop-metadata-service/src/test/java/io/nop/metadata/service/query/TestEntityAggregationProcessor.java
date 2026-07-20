package io.nop.metadata.service.query;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestEntityAggregationProcessor {

    @Test
    public void testImplementsAggregationProcessor() {
        assertTrue(new EntityAggregationProcessor() instanceof AggregationProcessor);
    }

    @Test
    public void testExecuteWithNullContextThrowsNpe() {
        EntityAggregationProcessor processor = new EntityAggregationProcessor();
        assertThrows(NullPointerException.class, () -> processor.execute(null));
    }

    @Test
    public void testCanInstantiate() {
        EntityAggregationProcessor processor = new EntityAggregationProcessor();
        assertNotNull(processor);
    }
}
