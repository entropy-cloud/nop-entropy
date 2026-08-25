/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestOrmComponentModel {

    private static OrmComponentPropModel prop(String name, int propId) {
        OrmColumnModel col = new OrmColumnModel();
        col.setName(name);
        col.setCode(name.toUpperCase());
        col.setPropId(propId);

        OrmComponentPropModel prop = new OrmComponentPropModel();
        prop.setName(name);
        prop.setColumnModel(col);
        return prop;
    }

    @Test
    public void testGetColumnPropIdsFollowsDeclarationOrder() {
        OrmComponentModel comp = new OrmComponentModel();
        // "B"(hash 66->桶2) 与 "a"(hash 97->桶1)：HashMap.values() 的哈希序为 [a, B]，与声明顺序相反
        comp.setProps(List.of(prop("B", 2), prop("a", 1)));

        // 修复前按 HashMap 哈希序返回 [1, 2]，getColumnPropId() 取值不确定
        assertArrayEquals(new int[]{2, 1}, comp.getColumnPropIds());
        assertEquals(2, comp.getColumnPropId());
    }
}
