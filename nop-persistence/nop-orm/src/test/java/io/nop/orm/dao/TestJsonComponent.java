/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.app.SimsClass;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.component.JsonOrmComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestJsonComponent extends AbstractOrmTestCase {
    @Test
    public void testJsonMap() {
        IEntityDao<SimsClass> dao = daoProvider().daoFor(SimsClass.class);
        orm().runInSession(() -> {
            SimsClass entity = dao.getEntityById("11");
            JsonOrmComponent comp = (JsonOrmComponent) entity.orm_propValueByName("jsonExtComponent");
            assertNull(comp.getValue("a"));
            comp.setValue("a", "xx");
            assertEquals("xx", BeanTool.getComplexProperty(entity, "jsonExtComponent.a"));
            orm().flushSession();
            assertEquals("xx", BeanTool.getComplexProperty(entity, "jsonExtComponent.a"));

            assertEquals(comp, entity.orm_propValueByName("jsonExtComponent"));
        });

        orm().runInSession(() -> {
            SimsClass entity = dao.getEntityById("11");
            assertEquals("xx", BeanTool.getComplexProperty(entity, "jsonExtComponent.a"));
        });
    }

    /**
     * json列为null的实体在被读取过组件后参与flush，不允许把字符串"null"写回列
     */
    @Test
    public void testNullJsonColumnNotPollutedOnFlush() {
        IEntityDao<SimsClass> dao = daoProvider().daoFor(SimsClass.class);
        assertNull(queryJsonExt());

        orm().runInSession(() -> {
            SimsClass entity = dao.getEntityById("11");
            // 读取组件触发懒解析，jsonValue被缓存为null
            JsonOrmComponent comp = (JsonOrmComponent) entity.orm_propValueByName("jsonExtComponent");
            assertNull(comp.getValue("a"));

            // 因其他属性修改导致实体参与flush
            entity.setClassName("flush-with-null-json");
            orm().flushSession();
        });

        // 修复前：flushToEntity把stringify(null)得到的字符串"null"写回列
        assertNull(queryJsonExt(), "null json column must not be written back as string 'null'");
    }

    private Object queryJsonExt() {
        // 单列查询findFirst直接返回列值
        return jdbc().findFirst(new SQL("select JSON_EXT as V from sims_class where CLASS_ID='11'"));
    }

    @Test
    public void testUpdate() {
        IEntityDao<SimsClass> dao = daoProvider().daoFor(SimsClass.class);
        orm().runInSession(() -> {
            SimsClass simsClass = dao.newEntity();
            simsClass.setMajorId("112");
            simsClass.setClassId("112");
            simsClass.setClassName("11");
            simsClass.setJsonExt("{\"name\":\"John Doe\"}");
            dao.saveEntity(simsClass);
            SimsClass entity = dao.getEntityById("112");
            Object jsonExt = entity.getJsonExt();
            JsonOrmComponent jsonExtComponent = entity.getJsonExtComponent();
            assertEquals("John Doe",jsonExtComponent.getValue("name"));
            simsClass.setJsonExt("{\"name\":\"2222222\"}");
            simsClass.getJsonExtComponent().reset();
            Object jsonExt2 = entity.getJsonExt();
            JsonOrmComponent jsonExtComponent2 = entity.getJsonExtComponent();
            assertEquals("2222222",jsonExtComponent2.getValue("name"));
        });
    }
}
