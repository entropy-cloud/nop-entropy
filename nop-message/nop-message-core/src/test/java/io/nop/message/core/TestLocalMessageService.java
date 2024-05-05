package io.nop.message.core;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.message.IMessageService;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.message.core.reflection.ReflectionMessageSubscriptionRegistrar;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(testBeansFile = "/nop/message/beans/test.beans.xml")
public class TestLocalMessageService extends JunitBaseTestCase {
    @Inject
    IMessageService messageService;

    // 明确引入bean确保它会被初始化
    @Inject
    ReflectionMessageSubscriptionRegistrar registrar;

    @Inject
    MyMessageListener listener;

    @Test
    public void testReflectionRegistrar() {
        MyMessage message = new MyMessage();
        message.setName("aaa");

        messageService.send("test", message);
        assertEquals(1, listener.getTriggerCount());
    }
}
