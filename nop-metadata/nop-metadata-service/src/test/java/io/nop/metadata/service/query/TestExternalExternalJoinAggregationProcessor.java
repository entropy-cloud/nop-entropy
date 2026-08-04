package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestExternalExternalJoinAggregationProcessor {

    // ===== execute() 分派行为（P1-MA4-601：空洞测试 → 行为断言） =====

    /** execute() 对 self-join（左右端点为同一表）显式失败（ERR_AGGR_JOIN_SELF_JOIN）。 */
    @Test
    public void testExecuteWithSelfJoinThrows() {
        AggregationContext context = mock(AggregationContext.class);
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("t1");
        MetaJoinExecutor.Endpoint ep = MetaJoinExecutor.Endpoint.table(table);
        when(context.getLeftEndpoint()).thenReturn(ep);
        when(context.getRightEndpoint()).thenReturn(ep);

        ExternalExternalJoinAggregationProcessor processor = new ExternalExternalJoinAggregationProcessor();
        NopException ex = assertThrows(NopException.class, () -> processor.execute(context));
        assertEquals(NopMetadataErrors.ERR_AGGR_JOIN_SELF_JOIN.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testExternalTableFromForJoinSqlType() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("sql");
        table.setSourceSql("SELECT * FROM EMP");
        String from = AggregationHelper.externalTableFromForJoin(table, "r");
        assertEquals("(SELECT * FROM EMP) r", from);
    }

    @Test
    public void testExternalTableFromForJoinExternalType() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("external");
        table.setTableName("EMP");
        String from = AggregationHelper.externalTableFromForJoin(table, "r");
        assertEquals("EMP r", from);
    }
}
