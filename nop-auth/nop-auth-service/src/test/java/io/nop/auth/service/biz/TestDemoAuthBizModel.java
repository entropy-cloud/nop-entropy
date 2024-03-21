package io.nop.auth.service.biz;

import io.nop.autotest.junit.JunitBaseTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDemoAuthBizModel extends JunitBaseTestCase {

    @Inject
    DemoAuthBizModel bizModel;
    @Test
    public void testInjectValue(){
        assertEquals("my-test.data", bizModel.testField);
        assertEquals("my-test.value", bizModel.getTestValue());
    }
}
