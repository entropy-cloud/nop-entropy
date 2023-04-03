package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Arrays;

@NopTestConfig()
public class TestManyToManyProp extends JunitAutoTestCase {
    @Inject
    IOrmTemplate ormTemplate;

    @Override
    public void initBeans() {
        super.initBeans();
    }

    @EnableSnapshot
    @Test
    public void testSave() {
        ormTemplate.runInSession(() -> {
            NopAuthRole role = (NopAuthRole) ormTemplate.newEntity(NopAuthRole.class.getName());
            role.setRoleId("test");
            role.setRoleName("testRole");
            String userId = getNopUserId();
            role.setRelatedUserIdList(Arrays.asList(userId));
            role.setRelatedUserIdList(Arrays.asList(userId));
            ormTemplate.save(role);
        });
    }

    String getNopUserId() {
        NopAuthUser user = ormTemplate.findFirst(SQL.begin().sql("select o from NopAuthUser o where o.userName='nop'").end());
        return user.getUserId();
    }
}
