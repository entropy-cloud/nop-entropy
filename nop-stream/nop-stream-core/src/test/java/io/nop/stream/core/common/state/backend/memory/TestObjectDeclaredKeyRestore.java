/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R5 ST-02 regression pins: operators that provision a keyed backend with the
 * Object.class declaration (EquiJoin/OverWindow/ProcessOperator) must still
 * produce restorable snapshots. The snapshot header records the effective key
 * type (first-key capture) and the restore side lets a concrete header override
 * the Object declaration, so keys re-materialize with real equals/hashCode
 * semantics. Reverting either the header source ({@code getSnapshotKeyType()})
 * or the restore-side override turns these pins red.
 */
class TestObjectDeclaredKeyRestore {

    @DataBean
    public static class BeanKey {
        private String id;

        public BeanKey() {
        }

        public BeanKey(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BeanKey)) {
                return false;
            }
            return id != null && id.equals(((BeanKey) o).id);
        }

        @Override
        public int hashCode() {
            return id == null ? 0 : id.hashCode();
        }
    }

    private static StateSnapshot jsonRoundTrip(StateSnapshot snapshot) {
        String json = JsonTool.serialize(snapshot.getStateData(), false);
        Map<String, Object> restored = JsonTool.parseMap(json);
        return new StateSnapshot(restored);
    }

    @Test
    void testLongKeySurvivesObjectDeclaredRoundTrip() throws Exception {
        MemoryKeyedStateBackend<Object> backend = new MemoryKeyedStateBackend<>(Object.class);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("n", Long.class);

        backend.setCurrentKey(42L);
        backend.getState(desc).update(7L);

        StateSnapshot snapshot = jsonRoundTrip(backend.snapshotState());

        MemoryKeyedStateBackend<Object> restored = new MemoryKeyedStateBackend<>(Object.class);
        restored.restoreState(snapshot);
        // JSON parsing yields Integer(42); the header-driven rematerialization
        // must rebuild Long(42) so this lookup hits.
        restored.setCurrentKey(42L);
        assertEquals(Long.valueOf(7L), restored.getState(desc).value());
    }

    @Test
    void testBeanKeySurvivesObjectDeclaredRoundTrip() throws Exception {
        MemoryKeyedStateBackend<Object> backend = new MemoryKeyedStateBackend<>(Object.class);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("n", Long.class);

        backend.setCurrentKey(new BeanKey("k1"));
        backend.getState(desc).update(9L);

        StateSnapshot snapshot = jsonRoundTrip(backend.snapshotState());

        MemoryKeyedStateBackend<Object> restored = new MemoryKeyedStateBackend<>(Object.class);
        restored.restoreState(snapshot);
        // A different-but-equal instance must hit after restore
        restored.setCurrentKey(new BeanKey("k1"));
        assertEquals(Long.valueOf(9L), restored.getState(desc).value());
    }
}
