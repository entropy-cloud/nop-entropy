/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 ORM-03：genFindLatestSql的列等值条件循环中，
 * revEnd列已在WHERE中以NOP_VER_MAX_VALUE字面量出现，不能再次作为绑定参数条件，
 * 否则transient新实体绑定的revEnd=null使条件恒不匹配，findLatest永远返回空。
 */
public class TestGenFindLatestSql {

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
        binders = new IDataParameterBinder[100];
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testFindLatestSqlDoesNotBindRevEndColumn() {
        IEntityModel entity = ormModel.getEntityModel("test.entity.TestRevisionEntity");
        assertTrue(entity.getNopRevEndVarPropId() > 0, "useRevision实体应自动补充revEnd内部列");

        EntitySQL sql = GenSqlHelper.genFindLatestSql(dialect, entity, binders, entity.getAllPropIds());

        assertFalse(sql.paramPropIds.indexOf(entity.getNopRevEndVarPropId()) >= 0,
                "revEnd列不能作为等值绑定参数（WHERE中已有rev_end=MAX字面量），参数列表: "
                        + sql.paramPropIds + "，SQL: " + sql.sql.getText());
    }
}
