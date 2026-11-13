/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.43 R4-03 (plan 2536) red ⑤ — W-05 (MA-14=C5-004, merged C5-008/C5-009):
 * the migration helpers must stream — single pass over the column family with
 * row-at-a-time read/migrate/write, no whole-CF keys+values dual-list heap
 * materialization (restore-time OOM face:作业无法拉起).
 *
 * <p>Drive form (S6 seam, white-box direct call on the package-private state
 * objects — TestRocksDBStateMigration is the round-trip base and keeps running
 * unchanged as the zero-rewrite anchor):
 * <ul>
 *   <li>red ⑤ streaming leg (deterministic, currently red): a counting value
 *       bean counts element constructions; when the FIRST migrate() invocation
 *       fires, at most one row's worth of elements may have been deserialized.
 *       Pre-fix {@code RocksDBListState.applyMigration} deserializes ALL rows'
 *       lists into a {@code List<List<Object>>} before migrating anything, so
 *       the first migrate sees N×M constructions — the streaming bound fails.</li>
 *   <li>red ⑤ round-trip legs (map/list/value three-instance matrix): migrated
 *       state reads back semantically equal across all three state kinds
 *       (guards "helper exists but an entry point did not converge" — every
 *       entry must stay on the shared streaming helper).</li>
 * </ul>
 * The compile-level red (streaming helper + delegating entries absent) is
 * registered separately in {@code _tmp/red-2536-GA-compile.log}.
 */
class TestRocksDBStateMigrationStreamingR403 {

    /** Test value bean: counts constructions so deserialization volume is observable. */
    @io.nop.api.core.annotations.data.DataBean
    public static class CountingBean {
        static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();
        private int value;

        public CountingBean() {
            CONSTRUCTIONS.incrementAndGet();
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    @TempDir
    File tempDir;

    private RocksDBKeyedStateBackend<String> openBackend(String name) {
        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                tempDir.toPath().resolve(name).toString(), String.class, 1, null);
        backend.setIncrementalCheckpointEnabled(false);
        return backend;
    }

    /**
     * red ⑤ streaming leg: list migration must deserialize at most ONE row's
     * list before the first migrate() invocation (row-at-a-time streaming).
     * Deterministic red pre-fix: N rows × M elements are ALL deserialized in
     * the collection loop before the first migrate runs.
     */
    @Test
    void listMigrationDeserializesAtMostOneRowBeforeFirstMigrate() {
        int rows = 8;
        int elementsPerRow = 5;
        RocksDBKeyedStateBackend<String> backend = openBackend("w05-list");
        try {
            ListStateDescriptor<CountingBean> oldDesc =
                    new ListStateDescriptor<>("m", CountingBean.class);
            RocksDBListState<CountingBean> state =
                    (RocksDBListState<CountingBean>) backend.getListState(oldDesc);
            for (int row = 0; row < rows; row++) {
                backend.setCurrentKey("key-" + row);
                List<CountingBean> list = new ArrayList<>();
                for (int i = 0; i < elementsPerRow; i++) {
                    CountingBean bean = new CountingBean();
                    bean.setValue(row * 100 + i);
                    list.add(bean);
                }
                state.addAll(list);
            }

            CountingBean.CONSTRUCTIONS.set(0);
            AtomicInteger firstMigrateObservation = new AtomicInteger(-1);
            state.applyMigration(migrationRecordingFirstMigrate(firstMigrateObservation));

            int atFirstMigrate = firstMigrateObservation.get();
            assertTrue(atFirstMigrate >= 0, "migration must invoke migrate at least once");
            assertTrue(atFirstMigrate <= elementsPerRow,
                    "row-at-a-time streaming: at most ONE row's elements (" + elementsPerRow
                            + ") may be deserialized before the first migrate() invocation, got "
                            + atFirstMigrate + " (pre-fix dual-list shape deserializes all "
                            + (rows * elementsPerRow) + " before migrating anything)");

            // Round-trip semantic equivalence on the same drive (list leg).
            ListStateDescriptor<CountingBean> newDesc =
                    new ListStateDescriptor<>("m", CountingBean.class);
            state.replaceDescriptor(newDesc);
            for (int row = 0; row < rows; row++) {
                backend.setCurrentKey("key-" + row);
                List<CountingBean> migrated = new ArrayList<>();
                state.get().forEach(migrated::add);
                assertEquals(elementsPerRow, migrated.size(), "row " + row + " element count preserved");
                assertEquals(row * 100 + 1, migrated.get(0).getValue(), "row " + row + " first element migrated (+1)");
            }
            backend.close();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** red ⑤ round-trip leg (map instance): map values migrate in place, map keys preserved. */
    @Test
    void mapMigrationRoundTripSemanticEquivalence() {
        RocksDBKeyedStateBackend<String> backend = openBackend("w05-map");
        try {
            MapStateDescriptor<String, CountingBean> oldDesc =
                    new MapStateDescriptor<>("mm", String.class, CountingBean.class);
            RocksDBMapState<String, CountingBean> state =
                    (RocksDBMapState<String, CountingBean>) backend.getMapState(oldDesc);
            int keys = 16;
            for (int i = 0; i < keys; i++) {
                backend.setCurrentKey("row-" + i);
                CountingBean bean = new CountingBean();
                bean.setValue(i);
                state.put("uk-" + i, bean);
            }

            state.applyMigration(plusOne());

            MapStateDescriptor<String, CountingBean> newDesc =
                    new MapStateDescriptor<>("mm", String.class, CountingBean.class);
            state.replaceDescriptor(newDesc);
            for (int i = 0; i < keys; i++) {
                backend.setCurrentKey("row-" + i);
                CountingBean migrated = state.get("uk-" + i);
                assertTrue(migrated != null, "map key uk-" + i + " preserved");
                assertEquals(i + 1, migrated.getValue(), "map value migrated (+1)");
            }
            backend.close();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** red ⑤ round-trip leg (value instance): whole-value migration via the shared base helper. */
    @Test
    void valueMigrationRoundTripSemanticEquivalence() {
        RocksDBKeyedStateBackend<String> backend = openBackend("w05-value");
        try {
            ValueStateDescriptor<CountingBean> oldDesc =
                    new ValueStateDescriptor<>("vv", CountingBean.class);
            RocksDBValueState<CountingBean> state =
                    (RocksDBValueState<CountingBean>) backend.getState(oldDesc);
            int keys = 16;
            Map<String, Integer> expected = new LinkedHashMap<>();
            for (int i = 0; i < keys; i++) {
                backend.setCurrentKey("vk-" + i);
                CountingBean bean = new CountingBean();
                bean.setValue(i * 2);
                state.update(bean);
                expected.put("vk-" + i, i * 2);
            }

            ((MigratableKeyedState) state).applyMigration(plusOne());

            ValueStateDescriptor<CountingBean> newDesc =
                    new ValueStateDescriptor<>("vv", CountingBean.class);
            ((MigratableKeyedState) state).replaceDescriptor(newDesc);
            for (Map.Entry<String, Integer> entry : expected.entrySet()) {
                backend.setCurrentKey(entry.getKey());
                CountingBean migrated = state.value();
                assertTrue(migrated != null, "value key " + entry.getKey() + " preserved");
                assertEquals(entry.getValue() + 1, migrated.getValue(), "value migrated (+1)");
            }
            backend.close();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static StateMigrationFunction<Object, Object> plusOne() {
        return new StateMigrationFunction<Object, Object>() {
            @Override
            public Object migrate(Object old) {
                CountingBean bean = (CountingBean) old;
                CountingBean migrated = new CountingBean();
                migrated.setValue(bean.getValue() + 1);
                return migrated;
            }

            @Override
            public io.nop.stream.core.checkpoint.SerializerFingerprint sourceFingerprint() {
                return null;
            }

            @Override
            public io.nop.stream.core.checkpoint.SerializerFingerprint targetFingerprint() {
                return null;
            }
        };
    }

    /** plusOne migration that snapshots the construction counter at the FIRST migrate call. */
    private static StateMigrationFunction<Object, Object> migrationRecordingFirstMigrate(
            AtomicInteger firstMigrateObservation) {
        return new StateMigrationFunction<Object, Object>() {
            @Override
            public Object migrate(Object old) {
                if (firstMigrateObservation.get() < 0) {
                    // Snapshot the element-construction count at the FIRST migrate
                    // invocation — the streaming bound point.
                    firstMigrateObservation.set(CountingBean.CONSTRUCTIONS.get());
                }
                CountingBean bean = (CountingBean) old;
                CountingBean migrated = new CountingBean();
                migrated.setValue(bean.getValue() + 1);
                return migrated;
            }

            @Override
            public io.nop.stream.core.checkpoint.SerializerFingerprint sourceFingerprint() {
                return null;
            }

            @Override
            public io.nop.stream.core.checkpoint.SerializerFingerprint targetFingerprint() {
                return null;
            }
        };
    }
}
