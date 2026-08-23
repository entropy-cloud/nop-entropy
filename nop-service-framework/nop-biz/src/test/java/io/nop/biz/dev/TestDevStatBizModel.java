package io.nop.biz.dev;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDevStatBizModel {

    /**
     * 清空统计是破坏性操作，必须挂在mutation通道而非只读的query通道。
     */
    @Test
    public void testClearStatsIsMutationNotQuery() throws Exception {
        Method clearStats = DevStatBizModel.class.getMethod("clearStats");

        // 修复前：clearStats标注为@BizQuery，只读通道即可触发统计清空
        assertNotNull(clearStats.getAnnotation(BizMutation.class));
    }

    @Test
    public void testStatQueriesRemainQueries() throws Exception {
        assertNotNull(DevStatBizModel.class.getMethod("jdbcSqlStats", Boolean.class).getAnnotation(BizQuery.class));
        assertNotNull(DevStatBizModel.class.getMethod("rpcServerStats", Boolean.class).getAnnotation(BizQuery.class));
        assertNotNull(DevStatBizModel.class.getMethod("rpcClientStats", Boolean.class).getAnnotation(BizQuery.class));
        assertTrue(true);
    }
}
