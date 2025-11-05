/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.app.SimsCollege;
import io.nop.app.SimsCollegeEx;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCascadeFlush extends AbstractOrmTestCase {
    @Test
    public void testOneToOne() {
        orm().runInSession(() -> {
            SimsCollege college = new SimsCollege();
            college.setCollegeId("123");
            college.setCollegeName("main");
            college.setIntro("main-info");

            SimsCollegeEx ex = new SimsCollegeEx();
            ex.setExtInfo("ext-info");
            college.setCollegeEx(ex);

            orm().save(college);
            orm().flushSession();

            orm().clearSession();
            college = daoProvider().daoFor(SimsCollege.class).getEntityById("123");
            assertEquals("ext-info", college.getCollegeEx().getExtInfo());
        });
    }

    @Test
    public void testOneToOneDelayInit() {
        orm().runInSession(() -> {
            SimsCollege college = (SimsCollege) orm().newEntity(SimsCollege.class.getName());
            college.setCollegeName("main");
            college.setIntro("main-info");

            SimsCollegeEx ex = new SimsCollegeEx();
            ex.setExtInfo("ext-info");
            college.setCollegeEx(ex);

            college.setCollegeId("123");

            orm().save(college);
            orm().flushSession();

            orm().clearSession();
            college = daoProvider().daoFor(SimsCollege.class).getEntityById("123");
            assertEquals("ext-info", college.getCollegeEx().getExtInfo());
        });
    }
}
