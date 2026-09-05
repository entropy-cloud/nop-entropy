/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.support;

import io.nop.app.SimsClass;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 回归覆盖审查报告 ORM-08：未调用next()直接调用iterator().remove()时，
 * 必须直接抛IllegalStateException，不能先把null放入removedEntities
 * （否则后续flush遍历orm_removed()时对null调用orm_state()抛NPE）。
 */
public class TestOrmEntitySetIteratorRemove {

    @Test
    public void testRemoveWithoutNextDoesNotCorruptRemovedEntities() {
        SimsClass owner = new SimsClass();
        OrmEntitySet<SimsClass> set = new OrmEntitySet<>(owner, "children", null, null, SimsClass.class);

        Iterator<SimsClass> it = set.iterator();
        assertThrows(IllegalStateException.class, it::remove, "未调用next()时remove必须抛IllegalStateException");

        assertNull(set.orm_removed(), "不应向removedEntities写入null元素");
    }
}
