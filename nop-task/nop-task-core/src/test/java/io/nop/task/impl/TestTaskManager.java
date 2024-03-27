package io.nop.task.impl;

import org.junit.jupiter.api.Test;

public class TestTaskManager extends AbstractTaskTestCase {
    @Test
    public void testXpl01() {
        runTask("test/xpl01");
    }
}