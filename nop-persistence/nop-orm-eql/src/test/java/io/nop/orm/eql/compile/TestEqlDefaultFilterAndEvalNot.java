package io.nop.orm.eql.compile;

import io.nop.api.core.beans.TreeBean;
import io.nop.xlang.ast.Expression;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.eval.SqlExprTransformHelper;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmToManyReferenceModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.6c（plan 2371）：簇 E 回归（P1-2.2-009 DML 缺省过滤对齐 + P1-2.2-011 eval 否定语义）。
 */
public class TestEqlDefaultFilterAndEvalNot {

    @BeforeAll
    public static void init() {
        TestEqlCompileSql.init();
    }

    /** 租户 + 逻辑删除模型（列在 init 前就位）。 */
    static TestEqlCompileSql.TestCompileContext tenantContext() {
        OrmEntityModel user = new OrmEntityModel();
        user.setName("AppTUser");
        user.setTableName("APP_T_USER");
        user.setQuerySpace("spaceA");
        user.setUseTenant(true);
        user.setTenantProp("tenantId");
        user.setUseLogicalDelete(true);
        user.setDeleteFlagProp("deleted");
        OrmColumnModel userPk = TestEqlCompileSql.col("id", "SID", 1, true);
        OrmColumnModel userName = TestEqlCompileSql.col("name", "NAME", 2, false);
        OrmColumnModel userTenant = TestEqlCompileSql.col("tenantId", "NOP_TENANT_ID", 3, false);
        OrmColumnModel userDeleted = TestEqlCompileSql.col("deleted", "DELETED", 4, false);
        user.setColumns(new ArrayList<>(Arrays.asList(userPk, userName, userTenant, userDeleted)));
        user.init();

        TestEqlCompileSql.TestCompileContext ctx = new TestEqlCompileSql.TestCompileContext();
        ctx.entities.put("AppTUser", user);
        return ctx;
    }

    // ===== P1-2.2-009：DML 缺省过滤注入 =====

    @Test
    public void testUpdateInjectsTenantAndLogicalDeleteFilters() {
        ICompiledSql compiled = TestEqlCompileSql.compile(tenantContext(),
                "update AppTUser o set o.name = 'x' where o.id = '1'");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        assertTrue(sql.contains("NOP_TENANT_ID"), "租户条件注入：" + sql);
        assertTrue(sql.contains("DELETED"), "逻辑删除条件注入：" + sql);
    }

    @Test
    public void testDeleteInjectsTenantAndLogicalDeleteFilters() {
        ICompiledSql compiled = TestEqlCompileSql.compile(tenantContext(),
                "delete from AppTUser o where o.id = '1'");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        assertTrue(sql.contains("NOP_TENANT_ID"), "租户条件注入：" + sql);
        assertTrue(sql.contains("DELETED"), "逻辑删除条件注入：" + sql);
    }

    @Test
    public void testDisableLogicalDeleteSkipsDeleteFlag() {
        // 匿名子类覆盖开关（TestCompileContext.isDisableLogicalDelete）
        TestEqlCompileSql.TestCompileContext base = tenantContext();
        TestEqlCompileSql.TestCompileContext ctx = new TestEqlCompileSql.TestCompileContext() {
            @Override
            public boolean isDisableLogicalDelete() {
                return true;
            }
        };
        ctx.entities.putAll(base.entities);
        ICompiledSql compiled = TestEqlCompileSql.compile(ctx,
                "update AppTUser o set o.name = 'x' where o.id = '1'");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        assertTrue(sql.contains("NOP_TENANT_ID"), "租户条件不受开关影响：" + sql);
        assertFalse(sql.contains("DELETED"), "开关关闭后不注入逻辑删除条件：" + sql);
    }

    @Test
    public void testPlainModelUnaffected() {
        ICompiledSql compiled = TestEqlCompileSql.compile(TestEqlCompileSql.relationContext(),
                "update AppUser o set o.name = 'x' where o.id = '1'");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        assertFalse(sql.contains("NOP_TENANT_ID"), "非租户模型不注入：" + sql);
    }

    // ===== P1-2.2-011：eval 否定语义 =====

    @Test
    public void testNotInBecomesNotInFilter() {
        TreeBean filter = SqlExprTransformHelper.parseSqlToFilter(null, "o.status not in (1, 2)");
        assertEquals("notIn", filter.getTagName(), "not in 不得翻译为肯定 in：" + filter);
    }

    @Test
    public void testNotLikeBecomesNotFilter() {
        TreeBean filter = SqlExprTransformHelper.parseSqlToFilter(null, "o.name not like 'a%'");
        assertEquals("not", filter.getTagName(), "not like 不得翻译为肯定 like：" + filter);
        TreeBean child = (TreeBean) filter.getChildren().get(0);
        assertEquals("like", child.getTagName());
    }

    @Test
    public void testInStillPositiveWhenNoNot() {
        TreeBean filter = SqlExprTransformHelper.parseSqlToFilter(null, "o.status in (1, 2)");
        assertEquals("in", filter.getTagName());
    }

    @Test
    public void testExpressionSideNotWraps() {
        Expression expr = SqlExprTransformHelper.parseSqlToExpression(null, "o.name not like 'a%'");
        String text = String.valueOf(expr);
        // UnaryExpression 渲染为 ! 前缀形态
        assertTrue(text.contains("!"), "Expression 侧否定包裹：" + text);
    }
}
