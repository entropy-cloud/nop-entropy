/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.dao;

import io.nop.app.SimsClass;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.support.JsonOrmComponent;
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
}
