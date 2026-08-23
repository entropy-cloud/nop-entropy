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
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;
import test.entity.TestCompositeOneToOneMain;
import test.entity.TestCompositeOneToOneSub;

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

    @Test
    public void testOneToOneCascadeDelete() {
        orm().runInSession(() -> {
            TestCompositeOneToOneMain main = (TestCompositeOneToOneMain) orm()
                    .newEntity(TestCompositeOneToOneMain.class.getName());
            main.setFldA("a");
            main.setFldB("b");
            main.setIntValue(1);

            TestCompositeOneToOneSub sub = (TestCompositeOneToOneSub) orm()
                    .newEntity(TestCompositeOneToOneSub.class.getName());
            sub.setIntValue(2);
            main.setSub(sub);

            orm().save(main);
            orm().flushSession();

            assertEquals(1, countTable("TEST_COMPOSITE_ONE_TO_ONE_MAIN"));
            assertEquals(1, countTable("TEST_COMPOSITE_ONE_TO_ONE_SUB"));

            orm().delete(main);
            orm().flushSession();
        });

        // cascadeDelete=true的to-one子表必须随主表一并删除，修复前子表记录残留
        assertEquals(0, countTable("TEST_COMPOSITE_ONE_TO_ONE_MAIN"));
        assertEquals(0, countTable("TEST_COMPOSITE_ONE_TO_ONE_SUB"));
    }

    private long countTable(String tableName) {
        Object v = jdbc().findFirst(new SQL("select count(*) as CNT from " + tableName));
        return ((Number) v).longValue();
    }
}
