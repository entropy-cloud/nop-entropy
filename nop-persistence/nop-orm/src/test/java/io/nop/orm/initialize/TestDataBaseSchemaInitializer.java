/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.initialize;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestDataBaseSchemaInitializer {

    private String[] savedQuerySpaces;

    @BeforeEach
    public void setUp() {
        savedQuerySpaces = DataBaseSchemaInitializer.specifyQuerySpaces;
        // 模拟dbtool等外部调用方：未通过IoC实例化bean，静态字段未被赋值
        DataBaseSchemaInitializer.specifyQuerySpaces = null;
    }

    @AfterEach
    public void tearDown() {
        DataBaseSchemaInitializer.specifyQuerySpaces = savedQuerySpaces;
    }

    /**
     * 静态方法splitByQuerySpace可能在bean未被实例化的情况下被外部直接调用，
     * 此时specifyQuerySpaces为null，不应抛出NPE
     */
    @Test
    public void testSplitByQuerySpaceWithoutBeanInit() {
        OrmEntityModel entityModel = new OrmEntityModel();
        entityModel.setName("test.TestEntity");

        Map<String, List<IEntityModel>> map = DataBaseSchemaInitializer
                .splitByQuerySpace(Collections.singletonList(entityModel));

        assertEquals(1, map.size());
        assertSame(entityModel, map.values().iterator().next().get(0));
    }

    /**
     * 配置了specifyQuerySpaces时只保留指定querySpace中的实体
     */
    @Test
    public void testSplitByQuerySpaceWithSpecifyQuerySpaces() {
        OrmEntityModel entityModel = new OrmEntityModel();
        entityModel.setName("test.TestEntity");
        entityModel.setQuerySpace("other");

        DataBaseSchemaInitializer.specifyQuerySpaces = new String[]{"track"};

        Map<String, List<IEntityModel>> map = DataBaseSchemaInitializer
                .splitByQuerySpace(Collections.singletonList(entityModel));

        assertEquals(0, map.size());
    }
}
