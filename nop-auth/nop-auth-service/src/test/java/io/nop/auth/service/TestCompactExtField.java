/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.auth.dao.entity.NopAuthDept;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.orm.support.IOrmCompactExtFieldSupport;
import jakarta.inject.Inject;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestCompactExtField extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testUserExtField() {
        IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
        NopAuthUser user = dao.newEntity();
        user.setUserName("testUser");
        user.setNickName("Test User");
        user.setOpenId("open123");
        user.setGender(1);
        user.setUserType(0);
        user.setStatus(1);

        assertTrue(user instanceof IOrmCompactExtFieldSupport);
        IOrmCompactExtFieldSupport extSupport = (IOrmCompactExtFieldSupport) user;

        assertNull(extSupport.getExtValue("ext1"));

        extSupport.setExtValue("ext1", "1");
        assertEquals("1", extSupport.getExtValue("ext1"));

        extSupport.setExtValue("ext1", "0");
        assertEquals("0", extSupport.getExtValue("ext1"));

        extSupport.setExtValue("ext1", null);
        assertNull(extSupport.getExtValue("ext1"));
    }

    @Test
    public void testDeptExtField() {
        IEntityDao<NopAuthDept> dao = daoProvider.daoFor(NopAuthDept.class);
        NopAuthDept dept = dao.newEntity();
        dept.setDeptName("Test Dept");

        assertTrue(dept instanceof IOrmCompactExtFieldSupport);
        IOrmCompactExtFieldSupport extSupport = (IOrmCompactExtFieldSupport) dept;

        assertNull(extSupport.getExtValue("ext1"));

        extSupport.setExtValue("ext1", "Y");
        assertEquals("Y", extSupport.getExtValue("ext1"));

        extSupport.setExtValue("ext1", null);
        assertNull(extSupport.getExtValue("ext1"));
    }

    @Test
    public void testExtFlagsPersistence() {
        IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
        NopAuthUser user = dao.newEntity();
        user.setUserName("persistTest");
        user.setNickName("Persist Test");
        user.setOpenId("open456");
        user.setGender(1);
        user.setUserType(0);
        user.setStatus(1);
        user.setPassword("test123");
        user.setTenantId("0");

        IOrmCompactExtFieldSupport extSupport = (IOrmCompactExtFieldSupport) user;
        extSupport.setExtValue("ext1", "1");
        extSupport.setExtValue("ext2", "0");

        String flagsBefore = extSupport.getExtFlags();
        
        dao.saveEntity(user);

        NopAuthUser loaded = dao.getEntityById(user.getUserId());
        IOrmCompactExtFieldSupport loadedExt = (IOrmCompactExtFieldSupport) loaded;

        assertEquals(flagsBefore, loadedExt.getExtFlags());
    }

    @Test
    public void testGetExtBoolean() {
        IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
        NopAuthUser user = dao.newEntity();
        user.setUserName("boolTest");

        IOrmCompactExtFieldSupport extSupport = (IOrmCompactExtFieldSupport) user;

        assertNull(extSupport.getExtBoolean("ext1"));

        extSupport.setExtBoolean("ext1", true);
        assertTrue(extSupport.getExtBoolean("ext1"));
        assertEquals("1", extSupport.getExtValue("ext1"));

        extSupport.setExtBoolean("ext1", false);
        assertFalse(extSupport.getExtBoolean("ext1"));
        assertEquals("0", extSupport.getExtValue("ext1"));

        extSupport.setExtBoolean("ext1", null);
        assertNull(extSupport.getExtBoolean("ext1"));
    }

    @Test
    public void testGetExtValues() {
        IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
        NopAuthUser user = dao.newEntity();
        user.setUserName("valuesTest");

        IOrmCompactExtFieldSupport extSupport = (IOrmCompactExtFieldSupport) user;
        extSupport.setExtValue("ext1", "A");
        extSupport.setExtValue("ext3", "C");

        var values = extSupport.getExtValues();
        assertEquals("A", values.get("ext1"));
        assertEquals("C", values.get("ext3"));
    }

    @Test
    public void testSetExtValues() {
        IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
        NopAuthUser user = dao.newEntity();
        user.setUserName("setValuesTest");

        IOrmCompactExtFieldSupport extSupport = (IOrmCompactExtFieldSupport) user;
        
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("ext1", "X");
        values.put("ext2", "Y");
        extSupport.setExtValues(values);

        assertEquals("X", extSupport.getExtValue("ext1"));
        assertEquals("Y", extSupport.getExtValue("ext2"));
    }
}
