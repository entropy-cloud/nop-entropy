package io.nop.job.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDiagProxy extends JunitBaseTestCase {

    @Test
    public void diag() {
        Object bean = io.nop.api.core.ioc.BeanContainer.tryGetBean("biz_NopJobFire");
        if (bean == null) {
            System.out.println("=== BEAN NOT FOUND: biz_NopJobFire");
            return;
        }
        System.out.println("=== PROXY CLASS: " + bean.getClass().getName());
        System.out.println("=== PROXY INTERFACES: " + Arrays.toString(bean.getClass().getInterfaces()));
        System.out.println("=== INSTANCEOF INopJobFireBiz: " + (bean instanceof io.nop.job.biz.INopJobFireBiz));
    }
}
