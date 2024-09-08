package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.dao.entity.NopAuthDept;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.OrmErrors;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
@NopTestProperty(name = "nop.orm.enable-tenant-by-default", value = "true")
public class TestTenant extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testCloneInstance() {
        IEntityDao<NopAuthDept> dao = daoProvider.daoFor(NopAuthDept.class);
        NopAuthDept dept = dao.newEntity();
        IEntityModel model = dept.orm_entityModel();
        for (IColumnModel col : model.getColumns()) {
            dept.orm_propValue(col.getPropId(), null);
        }
        dept.setDeptName("a");
        dept.setRemark("55");
        NopAuthDept copy = dept.cloneInstance();
        assertEquals("a", copy.getDeptName());
    }

    @Test
    public void testSave() {
        IEntityDao<NopAuthDept> dao = daoProvider.daoFor(NopAuthDept.class);

        try {
            ContextProvider.runWithTenant("1", () -> {
                NopAuthDept dept = dao.newEntity();
                dept.setDeptName("a");
                dept.setRemark("55");
                dept.orm_propValueByName("nopTenantId", "0");

                dao.saveEntity(dept);
                return null;
            });
            fail();
        } catch (NopException e) {
            assertEquals(OrmErrors.ERR_ORM_NOT_ALLOW_PROCESS_ENTITY_IN_OTHER_TENANT.getErrorCode(), e.getErrorCode());
        }
    }
}
