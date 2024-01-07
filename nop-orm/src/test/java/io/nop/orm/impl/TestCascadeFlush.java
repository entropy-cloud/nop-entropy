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
}
