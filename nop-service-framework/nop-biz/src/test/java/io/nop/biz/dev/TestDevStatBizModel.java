package io.nop.biz.dev;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.directive.Auth;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * 统计信息含SQL文本/数据源/慢查询参数等敏感内容，且默认启用（enableIfMissing=true），
     * 全部操作必须要求admin角色；修复前auth==null按公开访问处理。
     */
    @Test
    public void testAllOperationsRequireAdminRole() throws Exception {
        assertAdminAuth(DevStatBizModel.class.getMethod("clearStats"));
        assertAdminAuth(DevStatBizModel.class.getMethod("jdbcSqlStats", Boolean.class));
        assertAdminAuth(DevStatBizModel.class.getMethod("rpcServerStats", Boolean.class));
        assertAdminAuth(DevStatBizModel.class.getMethod("rpcClientStats", Boolean.class));
    }

    private static void assertAdminAuth(Method method) {
        Auth auth = method.getAnnotation(Auth.class);
        assertNotNull(auth, "method " + method.getName() + " must declare @Auth");
        assertEquals("admin", auth.roles(), "method " + method.getName() + " must require admin role");
    }
}
