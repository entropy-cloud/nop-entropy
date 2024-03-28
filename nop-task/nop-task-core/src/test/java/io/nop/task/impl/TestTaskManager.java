package io.nop.task.impl;

import org.junit.jupiter.api.Test;

public class TestTaskManager extends AbstractTaskTestCase {
    @Test
    public void testXpl01() {
        runTask("test/xpl-01");
    }

    @Test
    public void testSequential01() {
        runTask("test/sequential-01");
    }

    @Test
    public void testLoop01() {
        runTask("test/loop-01");
    }


    @Test
    public void testLoopN01() {
        runTask("test/loop-n-01");
    }

    @Test
    public void testChoose01() {
        runTask("test/choose-01");
    }
}