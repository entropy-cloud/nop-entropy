/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestOperatorLifecycle {

    @Test
    void testLifecycleOrder() throws Exception {
        List<String> lifecycleCalls = new ArrayList<>();

        TestOutput<String> testOutput = new TestOutput<>();
        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            @Override
            public void open() throws Exception {
                lifecycleCalls.add("open");
            }

            @Override
            public void processWatermark(Watermark mark) throws Exception {
                lifecycleCalls.add("processWatermark:" + mark.getTimestamp());
                super.processWatermark(mark);
            }

            @Override
            public void close() throws Exception {
                lifecycleCalls.add("close");
            }
        };

        operator.setOutput((Output) testOutput);
        operator.open();
        operator.processWatermark(new Watermark(100));
        operator.close();

        assertEquals(3, lifecycleCalls.size());
        assertEquals("open", lifecycleCalls.get(0));
        assertEquals("processWatermark:100", lifecycleCalls.get(1));
        assertEquals("close", lifecycleCalls.get(2));
    }

    @Test
    void testCloseIsAlwaysCalled() throws Exception {
        List<String> lifecycleCalls = new ArrayList<>();

        TestOutput<String> testOutput = new TestOutput<>();
        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            @Override
            public void open() throws Exception {
                lifecycleCalls.add("open");
            }

            @Override
            public void close() throws Exception {
                lifecycleCalls.add("close");
            }
        };

        operator.setOutput((Output) testOutput);
        operator.open();
        operator.close();

        assertTrue(lifecycleCalls.contains("open"));
        assertTrue(lifecycleCalls.contains("close"));
    }

    @Test
    void testProcessWatermarkExceptionPropagation() throws Exception {
        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            @Override
            public void processWatermark(Watermark mark) throws Exception {
                throw new RuntimeException("test exception in processWatermark");
            }
        };

        TestOutput<String> output = new TestOutput<>();
        operator.setOutput((Output) output);
        operator.open();

        assertThrows(RuntimeException.class, () ->
                operator.processWatermark(new Watermark(100)));
    }
}
