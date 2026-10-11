/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.ddl;

import io.nop.core.lang.sql.SQL;
import java.util.ArrayList;
import java.util.Collections;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.orm.AbstractJdbcTestCase;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmIndexModel;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestDdlSqlCreator extends AbstractJdbcTestCase {

    @Test
    public void testGenCreateTables() {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/test/orm/app.orm.xml");
        OrmModel ormModel = (OrmModel) DslModelHelper.loadDslModel(resource);
        String sql = DdlSqlCreator.forDialect("mysql").createTables(ormModel.getEntityModelsInTopoOrder(), true);
        System.out.println(sql);

        sql = DdlSqlCreator.forDialect("h2").createTables(ormModel.getEntities(), true);

        jdbcTemplate.executeMultiSql(new SQL(sql));
    }

    /**
     * R1.9 / 平台工单-2.5：createTables 必须物化 model 中的 index（含 unique=true）。
     * 此前 create 路径只输出 CreateTable，index 块一概丢失（deploy 与运行时自动建表同病）。
     */
    @Test
    public void testCreateTablesMaterializesIndexes() {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/test/orm/app.orm.xml");
        OrmModel ormModel = (OrmModel) DslModelHelper.loadDslModel(resource);

        // 为测试注入一个带 unique=true 与普通 index 的实体（若测试模型本身无 index 块）
        boolean hasIndex = ormModel.getEntityModels().stream().anyMatch(e -> !((io.nop.orm.model.OrmEntityModel) e).getIndexes().isEmpty());
        OrmEntityModel target = ormModel.getEntityModels().stream()
                .map(e -> (OrmEntityModel) e)
                .filter(e -> !e.getIndexes().isEmpty()).findFirst()
                .orElseGet(() -> {
                    OrmEntityModel e = (OrmEntityModel) ormModel.getEntityModels().iterator().next();
                    OrmIndexModel idx = new OrmIndexModel();
                    idx.setName("IDX_TEST_R19_UNIQUE");
                    idx.setUnique(true);
                    OrmColumnModel col = e.getColumns().iterator().next();
                    io.nop.orm.model.OrmIndexColumnModel colModel = new io.nop.orm.model.OrmIndexColumnModel();
                    colModel.setName(col.getName());
                    idx.setColumns(new ArrayList<>(Collections.singletonList(colModel)));
                    e.getIndexes().add(idx);
                    return e;
                });
        org.junit.jupiter.api.Assertions.assertTrue(
                target.getIndexes().stream().anyMatch(i -> Boolean.TRUE.equals(i.getUnique())) || !target.getIndexes().isEmpty(),
                "test premise: at least one index present");

        String sql = DdlSqlCreator.forDialect("mysql").createTables(ormModel.getEntityModelsInTopoOrder(), true);
        org.junit.jupiter.api.Assertions.assertTrue(
                sql.contains("create index") || sql.contains("create unique index"),
                "index DDL must be materialized by createTables: " + sql);
    }

    @Test
    public void testReflection() {
        IClassModel classModel = ReflectionManager.instance().getClassModel(DdlSqlCreator.class);
        IFunctionModel fn = classModel.getMethodsByName("createTables").getUniqueMethod(2);
        assertNotNull(fn);
    }
}
