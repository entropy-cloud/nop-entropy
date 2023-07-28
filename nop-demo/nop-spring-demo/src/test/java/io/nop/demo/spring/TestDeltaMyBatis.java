package io.nop.demo.spring;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.demo.spring.domain.SysUser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ComponentScan("io.nop.demo.spring")
@AutoConfigureTestDatabase
public class TestDeltaMyBatis {

    @Autowired
    SysUserMapper sysUserMapper;

    @Autowired
    SysUserMapperEx sysUserMapperEx;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMapper() {
        assertTrue(sysUserMapper == sysUserMapperEx);

        SysUser user = sysUserMapperEx.selectUserByUserName("nop");
        assertNotNull(user.getUserId());
    }
}
