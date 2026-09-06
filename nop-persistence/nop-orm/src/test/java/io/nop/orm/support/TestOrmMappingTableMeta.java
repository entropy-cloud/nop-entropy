/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.support;

import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModelConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestOrmMappingTableMeta {

    /**
     * getMappingPropEnDisplayName2必须读取NAME2配置。
     * 历史版本因复制粘贴误读NAME1，prop2的英文名总是取到prop1的配置
     */
    @Test
    public void testMappingPropEnDisplayName2() {
        OrmEntityModel mappingTable = new OrmEntityModel();
        mappingTable.setName("test.MappingTable");
        mappingTable.prop_set(OrmModelConstants.ORM_MAPPING_PROP_EN_DISPLAY_NAME1, "en-name-1");
        mappingTable.prop_set(OrmModelConstants.ORM_MAPPING_PROP_EN_DISPLAY_NAME2, "en-name-2");

        OrmMappingTableMeta meta = new OrmMappingTableMeta(mappingTable);

        assertEquals("en-name-1", meta.getMappingPropEnDisplayName1());
        assertEquals("en-name-2", meta.getMappingPropEnDisplayName2());

        // 未配置时返回null
        OrmEntityModel empty = new OrmEntityModel();
        empty.setName("test.EmptyMappingTable");
        OrmMappingTableMeta emptyMeta = new OrmMappingTableMeta(empty);
        assertNull(emptyMeta.getMappingPropEnDisplayName1());
        assertNull(emptyMeta.getMappingPropEnDisplayName2());
    }
}
