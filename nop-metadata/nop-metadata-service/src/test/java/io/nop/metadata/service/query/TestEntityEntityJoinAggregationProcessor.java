package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestEntityEntityJoinAggregationProcessor {

    // ===== execute() 分派行为（P1-MA4-601：空洞测试 → 行为断言） =====

    /** execute() 对 self-join（左右端点为同一实体）显式失败（ERR_AGGR_JOIN_SELF_JOIN）。 */
    @Test
    public void testExecuteWithSelfJoinThrows() {
        AggregationContext context = mock(AggregationContext.class);
        MetaJoinExecutor joinExecutor = mock(MetaJoinExecutor.class);
        when(context.joinExecutor()).thenReturn(joinExecutor);

        NopMetaEntity entity = new NopMetaEntity();
        entity.setMetaEntityId("e1");
        MetaJoinExecutor.Endpoint ep = MetaJoinExecutor.Endpoint.entity(entity);
        when(context.getLeftEndpoint()).thenReturn(ep);
        when(context.getRightEndpoint()).thenReturn(ep);

        EntityEntityJoinAggregationProcessor processor = new EntityEntityJoinAggregationProcessor();
        NopException ex = assertThrows(NopException.class, () -> processor.execute(context));
        assertEquals(NopMetadataErrors.ERR_AGGR_JOIN_SELF_JOIN.getErrorCode(), ex.getErrorCode());
    }
}
