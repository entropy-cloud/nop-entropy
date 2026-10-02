/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12: standalone tests for the per-key ordered buffer component (roadmap A5
 * evidence — the "per-key ordered buffer" keyed-state usage as an independently
 * testable artifact).
 */
public class TestPerKeyOrderedBuffer {

    @Test
    public void outOfOrderInsertsSortByEventTime() {
        PerKeyOrderedBuffer<String, String> buf = new PerKeyOrderedBuffer<>();
        buf.add("k", 30, "v30");
        buf.add("k", 10, "v10");
        buf.add("k", 20, "v20");
        assertEquals(List.of("v10", "v20", "v30"), buf.sortedView("k"));
    }

    @Test
    public void sameTimestampKeepsArrivalOrder() {
        PerKeyOrderedBuffer<String, String> buf = new PerKeyOrderedBuffer<>();
        buf.add("k", 10, "first");
        buf.add("k", 10, "second");
        assertEquals(List.of("first", "second"), buf.sortedView("k"),
                "equal timestamps keep arrival order (stable sort by insertion seq)");
    }

    @Test
    public void keysAreIsolated() {
        PerKeyOrderedBuffer<String, String> buf = new PerKeyOrderedBuffer<>();
        buf.add("a", 1, "va");
        buf.add("b", 2, "vb");
        assertEquals(List.of("va"), buf.sortedView("a"));
        assertEquals(List.of("vb"), buf.sortedView("b"));
    }

    @Test
    public void trimToWatermarkDropsConsumed() {
        PerKeyOrderedBuffer<String, String> buf = new PerKeyOrderedBuffer<>();
        buf.add("k", 10, "v10");
        buf.add("k", 20, "v20");
        buf.add("k", 30, "v30");
        assertEquals(2, buf.trimToWatermark("k", 20));
        assertEquals(List.of("v30"), buf.sortedView("k"));
        assertEquals(0, buf.trimToWatermark("k", 20), "re-trim is a no-op");
    }

    @Test
    public void serializableForOperatorDeepCopies() throws Exception {
        PerKeyOrderedBuffer<String, String> buf = new PerKeyOrderedBuffer<>();
        buf.add("k", 1, "v");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(buf);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            @SuppressWarnings("unchecked")
            PerKeyOrderedBuffer<String, String> copy = (PerKeyOrderedBuffer<String, String>) ois.readObject();
            assertEquals(List.of("v"), copy.sortedView("k"), "buffer must survive Java serialization");
        }
        assertTrue(buf.size("k") == 1);
    }
}
