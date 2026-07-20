package io.nop.metadata.service.query;

import io.nop.metadata.dao.entity.NopMetaTable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestExternalExternalJoinAggregationProcessor {

    @Test
    public void testImplementsAggregationProcessor() {
        assertTrue(new ExternalExternalJoinAggregationProcessor() instanceof AggregationProcessor);
    }

    @Test
    public void testExecuteWithNullContextThrowsNpe() {
        ExternalExternalJoinAggregationProcessor processor = new ExternalExternalJoinAggregationProcessor();
        assertThrows(NullPointerException.class, () -> processor.execute(null));
    }

    @Test
    public void testCanInstantiate() {
        ExternalExternalJoinAggregationProcessor processor = new ExternalExternalJoinAggregationProcessor();
        assertNotNull(processor);
    }

    @Test
    public void testExternalTableFromForJoinSqlType() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("sql");
        table.setSourceSql("SELECT * FROM EMP");
        String from = AggregationContext.externalTableFromForJoin(table, "r");
        assertEquals("(SELECT * FROM EMP) r", from);
    }

    @Test
    public void testExternalTableFromForJoinExternalType() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("external");
        table.setTableName("EMP");
        String from = AggregationContext.externalTableFromForJoin(table, "r");
        assertEquals("EMP r", from);
    }
}
