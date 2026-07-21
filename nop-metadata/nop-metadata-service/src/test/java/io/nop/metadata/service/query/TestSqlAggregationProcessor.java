package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    public void testExecuteWithUnsupportedTableTypeThrowsNopException() {
        AggregationContext context = mock(AggregationContext.class);
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("unsupported");
        when(context.getTable()).thenReturn(table);

        SqlAggregationProcessor processor = new SqlAggregationProcessor();
        NopException ex = assertThrows(NopException.class, () -> processor.execute(context));
        ErrorCode expected = NopMetadataErrors.ERR_AGGR_UNSUPPORTED_TABLE_TYPE;
        assertEquals(expected.getErrorCode(), ex.getErrorCode());
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
