/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.app.SimsClass;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

public class TestOrmComputeProp extends AbstractOrmTestCase {
    @Test
    public void testComputeProp() {
        IEntityDao<SimsClass> dao = daoProvider().daoFor(SimsClass.class);

        orm().runInSession(() -> {
            SimsClass entity = dao.getEntityById("11");
            assertEquals("CollegeAEx", entity.prop_get("collegeNameEx"));

            entity.prop_set("collegeNameEx", "c2Ex");
            assertEquals("c2", entity.getCollegeName());
        });
    }
}