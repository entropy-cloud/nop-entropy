package io.nop.biz.impl;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestDynamicCrudBizModel extends JunitBaseTestCase {

    @Inject
    IBizObjectManager bizObjectManager;

    @Test
    public void testDynamicObj() {
        IBizObject bo = bizObjectManager.getBizObject("MyBean");
        assertNotNull(bo);

        assertNotNull(bo.getAction("findPage"));
    }
}
