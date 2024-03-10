/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.demo.spring;

import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.api.DaoProvider;
import io.nop.demo.spring.domain.SysUser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ComponentScan({"io.nop.demo.spring"})
@AutoConfigureTestDatabase
public class TestDeltaMyBatis {
    static final Logger LOG = LoggerFactory.getLogger(TestDeltaMyBatis.class);


    @Autowired
    SysUserMapper sysUserMapper;

    @Autowired
    SysUserMapperEx sysUserMapperEx;

    @BeforeAll
    public static void init() {
        LOG.info("init test");
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMapper() {
        LOG.info("enter-mapper");
        assertTrue(sysUserMapper == sysUserMapperEx);

        List<NopAuthUser> users = DaoProvider.instance().daoFor(NopAuthUser.class).findAll();
        assertEquals(1, users.size());

        SysUser user = sysUserMapperEx.selectUserByUserName("nop");
        assertNotNull(user.getUserId());
    }
}
