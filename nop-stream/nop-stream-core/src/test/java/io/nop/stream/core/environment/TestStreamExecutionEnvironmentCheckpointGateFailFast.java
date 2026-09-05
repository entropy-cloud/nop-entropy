/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.environment;

import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-06 (Plan 2026-09-04-1326-1 Phase 2, adjudication D2=(b) backstop): on a classpath
 * with NO {@code ICheckpointExecutorFactory} (the core module's own test classpath —
 * no {@code META-INF/services} entry present), a declared {@code enableCheckpointing}
 * must fail fast with a typed error instead of silently falling through to the
 * non-checkpointed LOCAL execution.
 */
public class TestStreamExecutionEnvironmentCheckpointGateFailFast {

    @Test
    public void declaredCheckpointingWithoutAnyFactoryFailsFastTyped() {
        // precondition: no statically registered factory and no ServiceLoader hit on
        // this (core-only) test classpath
        assertNull(new StreamExecutionEnvironment().getCheckpointExecutorFactory(),
                "precondition: no static factory registered");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(1000);
        List<Integer> sink = new ArrayList<>();
        env.fromElements(1, 2, 3).sink(sink::add);

        StreamException ex = assertThrows(StreamException.class, () -> env.execute("f06-failfast"),
                "declared checkpointing without any factory must fail fast, not silently"
                        + " run without checkpoints");
        // env.execute() wraps execution failures in ERR_STREAM_JOB_EXECUTE_FAILED —
        // the F-06 typed gate error must be observable in the cause chain
        StringBuilder chain = new StringBuilder();
        for (Throwable t = ex; t != null; t = t.getCause()) {
            chain.append(t.getMessage()).append(" <- ");
        }
        assertTrue(chain.toString().contains("enableCheckpointing"),
                "typed error must name the declared-but-unwired checkpointing: " + chain);
    }

    @Test
    public void savepointWithoutAnyFactoryFailsFastTyped() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        StreamException ex = assertThrows(StreamException.class,
                () -> env.triggerSavepoint("/tmp/never"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("ICheckpointExecutorFactory"),
                "typed error must name the missing factory wiring: " + ex.getMessage());
    }

    @Test
    public void nonCheckpointedExecutionStillRunsLocally() throws Exception {
        // the fail-fast applies ONLY to declared checkpointing: a plain job without
        // enableCheckpointing keeps running on the LOCAL path
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        List<Integer> sink = new ArrayList<>();
        env.fromElements(1, 2, 3).map(i -> i * 2).sink(sink::add);
        StreamExecutionResult result = env.execute("f06-plain-local");
        assertEquals("f06-plain-local", result.getJobName());
        assertEquals(List.of(2, 4, 6), sink);
    }
}
