/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.ddl;

import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.orm.AbstractJdbcTestCase;
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

    @Test
    public void testReflection() {
        IClassModel classModel = ReflectionManager.instance().getClassModel(DdlSqlCreator.class);
        IFunctionModel fn = classModel.getMethodsByName("createTables").getUniqueMethod(2);
        assertNotNull(fn);
    }
}
