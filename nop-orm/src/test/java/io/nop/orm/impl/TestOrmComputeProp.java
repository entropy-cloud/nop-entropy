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
