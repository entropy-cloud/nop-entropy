package io.nop.metadata.service.query;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestMixedSameDbJoinAggregationProcessor {

    @Test
    public void testImplementsAggregationProcessor() {
        assertTrue(new MixedSameDbJoinAggregationProcessor() instanceof AggregationProcessor);
    }

    @Test
    public void testExecuteWithNullContextThrowsNpe() {
        MixedSameDbJoinAggregationProcessor processor = new MixedSameDbJoinAggregationProcessor();
        assertThrows(NullPointerException.class, () -> processor.execute(null));
    }

    @Test
    public void testCanInstantiate() {
        MixedSameDbJoinAggregationProcessor processor = new MixedSameDbJoinAggregationProcessor();
        assertNotNull(processor);
    }

    @Test
    public void testBuildEntityFromClauseWithSchema() {
        String from = AggregationHelper.buildEntityFromClause("EMP", "DBO", "l");
        assertEquals("DBO.EMP l", from);
    }

    @Test
    public void testBuildEntityFromClauseWithoutSchema() {
        String from = AggregationHelper.buildEntityFromClause("EMP", null, "l");
        assertEquals("EMP l", from);
    }
}
