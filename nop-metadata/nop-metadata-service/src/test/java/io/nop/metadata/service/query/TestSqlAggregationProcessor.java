package io.nop.metadata.service.query;

import io.nop.metadata.dao.entity.NopMetaTable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestSqlAggregationProcessor {

    @Test
    public void testImplementsAggregationProcessor() {
        assertTrue(new SqlAggregationProcessor() instanceof AggregationProcessor);
    }

    @Test
    public void testExecuteWithNullContextThrowsNpe() {
        SqlAggregationProcessor processor = new SqlAggregationProcessor();
        assertThrows(NullPointerException.class, () -> processor.execute(null));
    }

    @Test
    public void testCanInstantiate() {
        SqlAggregationProcessor processor = new SqlAggregationProcessor();
        assertNotNull(processor);
    }

    @Test
    public void testBuildFromClauseSqlTypeWithNullSourceSql() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("sql");
        assertThrows(RuntimeException.class, () -> AggregationContext.buildFromClause(table));
    }

    @Test
    public void testBuildFromClauseSqlTypeWithSourceSql() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("sql");
        table.setSourceSql("SELECT * FROM dual");
        String fromClause = AggregationContext.buildFromClause(table);
        assertEquals("(SELECT * FROM dual) _t", fromClause);
    }

    @Test
    public void testBuildFromClauseExternalType() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("external");
        table.setTableName("MY_TABLE");
        String fromClause = AggregationContext.buildFromClause(table);
        assertEquals("MY_TABLE", fromClause);
    }
}
