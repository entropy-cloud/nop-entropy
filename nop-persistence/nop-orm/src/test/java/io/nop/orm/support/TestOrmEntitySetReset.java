/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.support;

import io.nop.app.SimsClass;
import io.nop.app.SimsCollege;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestOrmEntitySetReset extends AbstractOrmTestCase {

    /**
     * flush成功后initialEntities与entities别名共享，此时调用orm_reset()不应清空集合。
     * 历史版本clear+addAll会把别名对象一并清空，导致已加载数据在内存中丢失
     */
    @Test
    public void testOrmResetAfterFlush() {
        orm().runInSession((IOrmSession session) -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege college = dao.loadEntityById("1");

            IOrmEntitySet<SimsClass> classes = college.getSimsClasses();
            assertEquals(1, classes.size());

            SimsClass extra = daoProvider().daoFor(SimsClass.class).newEntity();
            extra.setClassId("12");
            extra.setCollegeId("1");
            extra.setClassName("classB");
            orm().save(extra);
            classes.add(extra);

            session.flush();
            assertEquals(2, classes.size());

            // flush后集合与initialEntities别名共享，reset不应丢失已加载数据
            classes.orm_reset();
            assertEquals(2, classes.size());
            return null;
        });
    }
}
