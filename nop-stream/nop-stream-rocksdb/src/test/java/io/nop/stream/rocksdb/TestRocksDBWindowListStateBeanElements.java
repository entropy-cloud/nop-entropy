/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F-05 regression proof (plan 1326-2 Phase 1, nop-stream-rocksdb side): a window
 * ListState whose descriptor carries the real bean element type must round-trip
 * bean elements through the RocksDB JSON serde as beans. Pre-fix, the window
 * apply/aggregate+evictor/reduce/process paths created the descriptor with
 * {@code Object.class}, so {@code RocksDBListState.get()} handed the user
 * function LinkedHashMaps and the first window fire CCE'd (nop-stream-runtime
 * cannot host this test — it does not depend on this module).
 */
class TestRocksDBWindowListStateBeanElements {

    @TempDir
    File tempDir;

    @DataBean
    public static class TradeEvent {
        private long id;
        private long amount;

        public TradeEvent() {
        }

        public TradeEvent(long id, long amount) {
            this.id = id;
            this.amount = amount;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public long getAmount() {
            return amount;
        }

        public void setAmount(long amount) {
            this.amount = amount;
        }
    }

    /**
     * The window operator's descriptor-path access pattern (InternalListState with
     * window namespace, typed descriptor from the builder): add bean elements, read
     * them back — every element must be a {@code TradeEvent}, not a LinkedHashMap.
     */
    @Test
    void testBeanElementsRoundTripThroughTypedListState() throws Exception {
        RocksDBStateBackend factory = new RocksDBStateBackend(tempDir.getAbsolutePath());
        RocksDBKeyedStateBackend<String> backend =
                (RocksDBKeyedStateBackend<String>) factory.createKeyedStateBackend(String.class);
        try {
            ListStateDescriptor<TradeEvent> descriptor =
                    new ListStateDescriptor<>("window-contents", TradeEvent.class);
            InternalListState<String, String, TradeEvent> state =
                    backend.getInternalListState(descriptor);

            backend.setCurrentKey("key1");
            state.setCurrentNamespace("TW:0,200");
            state.add(new TradeEvent(1, 100));
            state.add(new TradeEvent(2, 200));

            // get() deserializes through RocksDBValueSerDe.deserializeList with the
            // descriptor's element type — the exact path that CCE'd pre-fix
            Iterable<TradeEvent> readBack = state.get();
            List<TradeEvent> elements = new ArrayList<>();
            for (TradeEvent e : readBack) {
                elements.add(e);
            }

            assertEquals(2, elements.size());
            for (TradeEvent e : elements) {
                assertEquals(TradeEvent.class, e.getClass(),
                        "RocksDB ListState must materialize bean elements when the descriptor "
                                + "carries the real element type (pre-fix: LinkedHashMap → CCE "
                                + "in the window function on first fire)");
            }
            assertEquals(1L, elements.get(0).getId());
            assertEquals(100L, elements.get(0).getAmount());
            assertEquals(2L, elements.get(1).getId());
            assertEquals(200L, elements.get(1).getAmount());
        } finally {
            backend.close();
        }
    }
}
