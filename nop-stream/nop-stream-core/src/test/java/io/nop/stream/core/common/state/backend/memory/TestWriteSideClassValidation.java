/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 ST-03 regression pin (plan 2325): the snapshot WRITE side enforces the same
 * class allowlist as the restore side. Before the fix a state whose declared
 * type sits outside {@code ClassNameValidator}'s allowlist produced a durable
 * checkpoint that succeeded at snapshot time and then failed loudly on EVERY
 * restore attempt (an unrecoverable durable asset). With the write-side gate,
 * the snapshot itself fails fast. Reverting the validation calls in
 * {@code MemoryStateSerDe.snapshotState/snapshotKeyedState} turns these tests
 * red.
 */
class TestWriteSideClassValidation {

    @DataBean
    public static class AllowedValue {
        private int v;

        public int getV() {
            return v;
        }

        public void setV(int v) {
            this.v = v;
        }
    }

    /** A real loadable class whose package is OUTSIDE the restore allowlist. */
    private static Class<?> disallowedClass() {
        try {
            return Class.forName("javax.management.MBeanServer");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("pin requires a loadable non-allowlisted class", e);
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void disallowedValueTypeFailsFastAtSnapshot() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        Class evil = disallowedClass();
        ValueStateDescriptor desc = new ValueStateDescriptor<>("evil", evil);

        backend.setCurrentKey("key1");
        ValueState state = backend.getState(desc);
        state.update("probe");

        StreamException e = assertThrows(StreamException.class, () -> backend.snapshotState(),
                "a state whose declared type is outside the allowlist must fail fast AT SNAPSHOT time "
                        + "instead of producing an unrestorable checkpoint");
        assertTrue(String.valueOf(e.getParam("className")).contains("javax.management"),
                () -> "the rejection must name the offending class: " + e.getParam("className"));
    }

    @Test
    void allowlistedTypesKeepSnapshottingAndRestoring() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<AllowedValue> desc = new ValueStateDescriptor<>("ok", AllowedValue.class);

        backend.setCurrentKey("key1");
        ValueState<AllowedValue> state = backend.getState(desc);
        AllowedValue v = new AllowedValue();
        v.setV(42);
        state.update(v);

        StateSnapshot snapshot = backend.snapshotState();
        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);
        restored.setCurrentKey("key1");
        assertEquals(42, restored.getState(desc).value().getV(),
                "allowlisted DataBean roundtrip must stay intact");
    }
}
