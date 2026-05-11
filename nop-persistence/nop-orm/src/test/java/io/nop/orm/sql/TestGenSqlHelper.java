/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmEntityFilterModel;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGenSqlHelper {

    private static OrmModel ormModel;
    private static IDialect dialect;
    private static IDataParameterBinder[] binders;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();

        ormModel = (OrmModel) DslModelHelper.loadDslModel(
                io.nop.core.resource.VirtualFileSystem.instance().getResource(
                        "/nop/test/orm/test-collection-filter.orm.xml"));
        dialect = DialectManager.instance().getDialect("mysql");
        // 创建空的 binders 数组，足够容纳所有 propId
        binders = new IDataParameterBinder[100];
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IEntityModel getEntity(String name) {
        return ormModel.getEntity(name);
    }

    /**
     * 无任何过滤条件时返回 false，不追加 SQL
     */
    @Test
    public void testNoFilter() {
        IEntityModel entity = getEntity("test.entity.TestNoFilterEntity");
        SQL.SqlBuilder sb = SQL.begin();
        sb.alwaysTrue();

        boolean result = GenSqlHelper.genCollectionFilterEx(sb, dialect, entity, null, binders);

        assertFalse(result);
        assertTrue(sb.end().getText().contains("1=1"));
    }

    /**
     * 固定过滤器：生成 AND STATUS = ? 条件
     */
    @Test
    public void testFixedFilter() {
        IEntityModel entity = getEntity("test.entity.TestFilterEntity");
        SQL.SqlBuilder sb = SQL.begin();
        sb.alwaysTrue();

        boolean result = GenSqlHelper.genCollectionFilterEx(sb, dialect, entity, null, binders);

        // hasFilter 不改变 append 返回值（lambda 里无法修改），但 SQL 中会追加条件
        assertFalse(result);
        String sql = sb.end().getText();
        assertTrue(sql.contains("STATUS"), "SQL should contain STATUS column: " + sql);
        assertTrue(sql.contains("="), "SQL should contain = operator: " + sql);

        // 验证 filter 的值被正确解析
        List<OrmEntityFilterModel> filters = entity.getFilters();
        assertEquals(1, filters.size());
        assertEquals("active", filters.get(0).getValue());
    }

    /**
     * 租户隔离：生成 AND NOP_TENANT_ID = ? 条件
     */
    @Test
    public void testTenantFilter() {
        IEntityModel entity = getEntity("test.entity.TestTenantEntity");

        IContext ctx = ContextProvider.getOrCreateContext();
        String oldTenant = ctx.getTenantId();
        ctx.setTenantId("myTenant");

        try {
            SQL.SqlBuilder sb = SQL.begin();
            sb.alwaysTrue();

            boolean result = GenSqlHelper.genCollectionFilterEx(sb, dialect, entity, null, binders);

            assertTrue(result);
            String sql = sb.end().getText();
            assertTrue(sql.contains("NOP_TENANT_ID"), "SQL should contain NOP_TENANT_ID: " + sql);
        } finally {
            ctx.setTenantId(oldTenant);
        }
    }

    /**
     * 逻辑删除：生成 AND DEL_FLAG = 0 条件
     */
    @Test
    public void testLogicalDeleteFilter() {
        IEntityModel entity = getEntity("test.entity.TestLogicalDeleteEntity");
        SQL.SqlBuilder sb = SQL.begin();
        sb.alwaysTrue();

        boolean result = GenSqlHelper.genCollectionFilterEx(sb, dialect, entity, null, binders);

        assertTrue(result);
        String sql = sb.end().getText();
        assertTrue(sql.contains("DEL_FLAG"), "SQL should contain DEL_FLAG: " + sql);
        assertTrue(sql.contains("0"), "SQL should contain 0 for not-deleted: " + sql);
    }

    /**
     * 版本修订：生成 AND NOP_REV_END_VER = 9223372036854775807 条件
     */
    @Test
    public void testRevisionFilter() {
        IEntityModel entity = getEntity("test.entity.TestRevisionEntity");
        SQL.SqlBuilder sb = SQL.begin();
        sb.alwaysTrue();

        boolean result = GenSqlHelper.genCollectionFilterEx(sb, dialect, entity, null, binders);

        assertTrue(result);
        String sql = sb.end().getText();
        assertTrue(sql.contains("NOP_REV_END_VER"), "SQL should contain NOP_REV_END_VER: " + sql);
        assertTrue(sql.contains(String.valueOf(Long.MAX_VALUE)),
                "SQL should contain Long.MAX_VALUE: " + sql);
    }

    /**
     * 所有条件组合：固定过滤 + 租户隔离 + 逻辑删除 同时生效
     */
    @Test
    public void testAllFiltersCombined() {
        IEntityModel entity = getEntity("test.entity.TestAllFilterEntity");

        IContext ctx = ContextProvider.getOrCreateContext();
        String oldTenant = ctx.getTenantId();
        ctx.setTenantId("myTenant");

        try {
            SQL.SqlBuilder sb = SQL.begin();
            sb.alwaysTrue();

            boolean result = GenSqlHelper.genCollectionFilterEx(sb, dialect, entity, null, binders);

            assertTrue(result);
            String sql = sb.end().getText();
            // 固定过滤
            assertTrue(sql.contains("TYPE"), "SQL should contain TYPE column: " + sql);
            // 租户
            assertTrue(sql.contains("NOP_TENANT_ID"), "SQL should contain NOP_TENANT_ID: " + sql);
            // 逻辑删除
            assertTrue(sql.contains("DEL_FLAG"), "SQL should contain DEL_FLAG: " + sql);
            // 应该有多个 and 连接
            assertTrue(sql.toLowerCase().contains("and"), "SQL should contain AND: " + sql);
        } finally {
            ctx.setTenantId(oldTenant);
        }
    }

    /**
     * 验证 genCollectionLoadSql 对带过滤条件的实体生成完整 SQL
     */
    @Test
    public void testCollectionLoadSqlWithFilter() {
        IEntityModel entity = getEntity("test.entity.TestFilterEntity");
        // TestFilterEntity 没有显式的 to-many 关系，
        // 所以我们直接测试 genCollectionFilterEx 的集成效果
        SQL.SqlBuilder sb = SQL.begin();
        sb.select().append(" * ").from();
        GenSqlHelper.table(sb, dialect, entity, null);
        sb.where().alwaysTrue();
        GenSqlHelper.genCollectionFilterEx(sb, dialect, entity, null, binders);

        SQL sql = sb.end();
        String text = sql.getText();
            assertTrue(text.toLowerCase().contains("test_filter_entity"),
                    "SQL should contain table name: " + text);
        assertTrue(text.contains("STATUS"), "SQL should contain STATUS filter: " + text);
    }
}
