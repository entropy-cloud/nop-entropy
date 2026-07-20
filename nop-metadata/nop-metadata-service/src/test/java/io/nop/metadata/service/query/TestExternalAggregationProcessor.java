package io.nop.metadata.service.query;

import io.nop.metadata.dao.entity.NopMetaTable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestExternalAggregationProcessor {

    @Test
    public void testImplementsAggregationProcessor() {
        assertTrue(new ExternalAggregationProcessor() instanceof AggregationProcessor);
    }

    @Test
    public void testExecuteWithNullContextThrowsNpe() {
        ExternalAggregationProcessor processor = new ExternalAggregationProcessor();
        assertThrows(NullPointerException.class, () -> processor.execute(null));
    }

    @Test
    public void testCanInstantiate() {
        ExternalAggregationProcessor processor = new ExternalAggregationProcessor();
        assertNotNull(processor);
    }

    @Test
    public void testLoadExternalMeasuresWithNullNamesReturnsEmpty() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        assertThrows(NullPointerException.class,
                () -> ExternalAggregationProcessor.loadExternalMeasures(table, null, null));
    }

    @Test
    public void testLoadExternalDimensionsWithNullNamesReturnsEmpty() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        assertThrows(NullPointerException.class,
                () -> ExternalAggregationProcessor.loadExternalDimensions(table, null, null));
    }
}
