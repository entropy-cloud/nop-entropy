package io.nop.metadata.service.query;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestEntityEntityJoinAggregationProcessor {

    @Test
    public void testImplementsAggregationProcessor() {
        assertTrue(new EntityEntityJoinAggregationProcessor() instanceof AggregationProcessor);
    }

    @Test
    public void testExecuteWithNullContextThrowsNpe() {
        EntityEntityJoinAggregationProcessor processor = new EntityEntityJoinAggregationProcessor();
        assertThrows(NullPointerException.class, () -> processor.execute(null));
    }

    @Test
    public void testCanInstantiate() {
        EntityEntityJoinAggregationProcessor processor = new EntityEntityJoinAggregationProcessor();
        assertNotNull(processor);
    }
}
