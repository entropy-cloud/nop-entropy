package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestMixedSameDbJoinAggregationProcessor {

    // ===== execute() 分派行为（P1-MA4-601：空洞测试 → 行为断言） =====

    /** execute() 对 entity 端点物理表名为空的混合 JOIN 显式失败（ERR_AGGR_JOIN_MIXED_ENTITY_TABLE_EMPTY）。 */
    @Test
    public void testExecuteWithEmptyEntityTableThrows() {
        AggregationContext context = mock(AggregationContext.class);
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("t1");
        NopMetaEntity entity = new NopMetaEntity();
        when(context.getLeftEndpoint()).thenReturn(MetaJoinExecutor.Endpoint.table(table));
        when(context.getRightEndpoint()).thenReturn(MetaJoinExecutor.Endpoint.entity(entity));

        MixedSameDbJoinAggregationProcessor processor = new MixedSameDbJoinAggregationProcessor();
        NopException ex = assertThrows(NopException.class, () -> processor.execute(context));
        assertEquals(NopMetadataErrors.ERR_AGGR_JOIN_MIXED_ENTITY_TABLE_EMPTY.getErrorCode(), ex.getErrorCode());
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
