/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.init;

import io.nop.commons.type.StdSqlType;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOrmEntityModelHasLazyColumn {

    static OrmColumnModel column(String name, String code, int propId, boolean primary, boolean lazy) {
        OrmColumnModel col = new OrmColumnModel();
        col.setName(name);
        col.setCode(code);
        col.setPropId(propId);
        col.setStdSqlType(StdSqlType.VARCHAR);
        col.setPrimary(primary);
        col.setLazy(lazy);
        return col;
    }

    static OrmEntityModel buildEntity(OrmColumnModel... cols) {
        OrmEntityModel entityModel = new OrmEntityModel();
        entityModel.setName("EntityA");
        entityModel.setTableName("tbl_a");
        List<OrmColumnModel> columns = new ArrayList<>();
        for (OrmColumnModel col : cols) {
            columns.add(col);
        }
        entityModel.setColumns(columns);

        OrmModel model = new OrmModel();
        model.setEntities(List.of(entityModel));
        model.init();
        return entityModel;
    }

    @Test
    public void testNoLazyColumn() {
        // 所有列均非 lazy：初始化器的别名优化应使 hasLazyColumn() 返回 false
        OrmEntityModel entity = buildEntity(
                column("sid", "SID", 1, true, false),
                column("name", "NAME", 2, false, false));

        // 修复前双重 toImmutable() 产生两个不同实例，引用比较恒为 true
        assertFalse(entity.hasLazyColumn());
        // eager/all 仍应包含全部属性
        assertTrue(entity.getEagerLoadProps().contains(1));
        assertTrue(entity.getEagerLoadProps().contains(2));
        assertTrue(entity.getAllPropIds().contains(1));
        assertTrue(entity.getAllPropIds().contains(2));
    }

    @Test
    public void testLazyColumnExists() {
        OrmEntityModel entity = buildEntity(
                column("sid", "SID", 1, true, false),
                column("bigText", "BIG_TEXT", 2, false, true));

        assertTrue(entity.hasLazyColumn());
        // lazy 列不应进入 eagerLoadProps
        assertTrue(entity.getEagerLoadProps().contains(1));
        assertFalse(entity.getEagerLoadProps().contains(2));
        assertTrue(entity.getAllPropIds().contains(2));
    }
}
