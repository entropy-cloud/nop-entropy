/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql;

import io.nop.core.initialize.CoreInitialization;
import io.nop.api.core.context.ContextProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.commons.text.marker.Markers.ValueMarker;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归覆盖审查报告 SQL-01/SQL-02：
 * <p>
 * SQL-01: appendExampleFilter在example已携带租户条件时，仍追加绑定currentTenantId的条件，
 * 两个租户值不同时AND条件恒为空，查询静默返回0行；
 * <p>
 * SQL-02: genUpdateByExample在updated实体的可初始化属性全部不可更新且无版本字段时，
 * SET段为空，deleteTail咬掉" set "的尾空格生成非法SQL，应显式抛错。
 * <p>
 * 注：报告中的SQL-03（orderBy非法列名NPE）经复核证伪——getColumn(name,false)本身
 * 就会抛出ERR_ORM_UNKNOWN_COLUMN可读错误，不存在NPE，故不设测试。
 */
public class TestGenSqlDefects {

    private static OrmModel filterModel;
    private static OrmModel defectModel;
    private static IDialect dialect;
    private static IDataParameterBinder[] binders;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();

        filterModel = (OrmModel) DslModelHelper.loadDslModel(
                io.nop.core.resource.VirtualFileSystem.instance().getResource(
                        "/nop/test/orm/test-collection-filter.orm.xml"));
        defectModel = (OrmModel) DslModelHelper.loadDslModel(
                io.nop.core.resource.VirtualFileSystem.instance().getResource(
                        "/nop/test/orm/sql-defect.orm.xml"));
        dialect = DialectManager.instance().getDialect("mysql");
        binders = new IDataParameterBinder[100];
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static IEntityModel getModel(IOrmModel model, String name) {
        return model.getEntityModel(name);
    }

    private static List<Object> boundValues(SQL sql) {
        List<Object> ret = new ArrayList<>();
        for (Object marker : sql.getMarkers()) {
            if (marker instanceof ValueMarker)
                ret.add(((ValueMarker) marker).getValue());
        }
        return ret;
    }

    /**
     * SQL-01: example已携带租户条件时，不应再追加currentTenantId过滤条件。
     */
    @Test
    public void testExampleTenantNotOverriddenByContextTenant() {
        IEntityModel model = getModel(filterModel, "test.entity.TestTenantEntity");
        assertTrue(model.getTenantPropId() > 0);

        DynamicOrmEntity example = new DynamicOrmEntity(model);
        example.orm_internalSet(model.getColumn("sid", true).getPropId(), 1L);
        example.orm_internalSet(model.getTenantPropId(), "ex-t");

        ContextProvider.getOrCreateContext().setTenantId("ctx-t");
        try {
            SQL sql = GenSqlHelper.genCountByExample(dialect, model, binders, example).end();

            List<Object> values = boundValues(sql);
            assertTrue(values.contains("ex-t"), "应绑定example携带的租户值，实际绑定: " + values);
            assertFalse(values.contains("ctx-t"),
                    "example已指定租户时不应追加上下文租户条件（两值AND恒为空导致静默0行），实际绑定: " + values);
        } finally {
            ContextProvider.getOrCreateContext().setTenantId(null);
        }
    }

    /**
     * SQL-02: 全部可初始化属性不可更新且无版本字段时，SET段为空，应显式抛错而非生成非法SQL。
     */
    @Test
    public void testUpdateByExampleEmptySetProducesValidSql() {
        IEntityModel model = getModel(defectModel, "test.entity.TestNoUpdatableEntity");

        DynamicOrmEntity example = new DynamicOrmEntity(model);
        example.orm_internalSet(model.getColumn("sid", true).getPropId(), 1L);

        DynamicOrmEntity updated = new DynamicOrmEntity(model);
        updated.orm_propValue(model.getColumn("name", true).getPropId(), "new-name");
        assertTrue(updated.orm_inited(), "updated实体应有已初始化属性");

        assertThrows(OrmException.class,
                () -> GenSqlHelper.genUpdateByExample(dialect, model, binders, example, updated),
                "SET段为空时应显式报错，而非生成deleteTail咬坏后的非法SQL");
    }

}
