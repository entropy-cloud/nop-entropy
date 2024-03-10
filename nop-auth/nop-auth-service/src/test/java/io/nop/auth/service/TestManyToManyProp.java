/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

@NopTestConfig(initDatabaseSchema = true, disableSnapshot = false)
//@NopTestProperty(name = "nop.auth.login.allow-create-default-user", value = "true")
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
