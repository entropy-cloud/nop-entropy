/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.43 R4-03 (plan 2536) red ⑫ bridge ② — W-12 (C8-012; adjudication C.6)
 * on the debezium CDC push→pull bridge: same envelope, same default, same
 * typed reject BEFORE the put as the message bridge (dual-bridge consistency
 * declaration). The 2511 last-emitted offset tracker chain is NOT touched —
 * the envelope guards only the queue entry (after offset extraction).
 *
 * <p>Drive form (S12 = small-cap injection + white-box
 * {@code measureRecordBytes} seam): oversized ChangeEvent / String payloads are
 * typed-rejected; the cap-1 boundary passes; the ChangeEvent depth-3
 * key/before/after measurement is pinned; non-positive bounds fail fast at
 * construction. Pre-fix red evidence = the compile face of this class (no
 * envelope constructor existed) + the dual-bridge behavioral leg on the
 * message side ({@code _tmp/red-2536-GD-compile.log}).
 */
class TestDebeziumCdcReaderByteBoundR403 {

    private static DebeziumConfig newConfig() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("r403-engine");
        return config;
    }

    private static ChangeEvent eventOfSize(int stringLength) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("v", "y".repeat(stringLength));
        ChangeEventMetadata metadata = new ChangeEventMetadata(
                "mysql", "r403", "db", "schema", "t", null, null, null, null, null);
        return new ChangeEvent(metadata, "c", null, after,
                Map.of("id", 1), System.currentTimeMillis());
    }

    @Test
    void measureChangeEventIsKeyBeforeAfterDepthBoundR403() {
        // key {"id":1} = 2 ("id") + 0 (Integer non-carrier) + after {"v": 10-char}
        // = 1 + 10 → total 13 code units under the declared caliber.
        ChangeEvent event = eventOfSize(10);
        assertEquals(2 + 0 + 1 + 10, DebeziumCdcSourceReader.measureRecordBytes(event));
        // Non-carrier type contributes 0 (declared residual).
        assertEquals(0L, DebeziumCdcSourceReader.measureRecordBytes(new Object()));
        assertEquals(0L, DebeziumCdcSourceReader.measureRecordBytes(null));
    }

    @Test
    void oversizedRecordTypedRejectedAtCollectR403() throws Exception {
        DebeziumCdcSourceReader reader = new DebeziumCdcSourceReader(
                newConfig(), null, null, 100L /* small cap */);
        try {
            java.lang.reflect.Field ctxField = DebeziumCdcSourceReader.class
                    .getDeclaredField("queue");
            ctxField.setAccessible(true);
            java.lang.reflect.Field runnerF = DebeziumCdcSourceReader.class
                    .getDeclaredField("runnerThread");
            runnerF.setAccessible(true);
            // The envelope is enforced inside QueueSourceContext.collect; drive it
            // directly through the private inner class (package seam, same shape
            // the engine host uses).
            Class<?> ctxClass = null;
            for (Class<?> c : DebeziumCdcSourceReader.class.getDeclaredClasses()) {
                if (c.getSimpleName().equals("QueueSourceContext")) {
                    ctxClass = c;
                    break;
                }
            }
            assertTrue(ctxClass != null, "QueueSourceContext must exist");
            java.lang.reflect.Constructor<?> ctor = ctxClass
                    .getDeclaredConstructor(DebeziumCdcSourceReader.class);
            ctor.setAccessible(true);
            Object ctx = ctor.newInstance(reader);
            java.lang.reflect.Method collect = ctxClass.getMethod("collect", ChangeEvent.class);

            // cap-1 boundary: key(2) + field name(1) + 90-char after-value = 93 <= 100.
            ChangeEvent small = eventOfSize(90);
            // queue is null before launchEngine — inject a queue (S12 seam shape).
            java.util.concurrent.BlockingQueue<Object> queue =
                    new java.util.concurrent.ArrayBlockingQueue<>(256);
            ctxField.setAccessible(true);
            ctxField.set(reader, queue);
            collect.invoke(ctx, small);
            assertEquals(1, queue.size(), "cap-1 record must queue normally");

            var e = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> collect.invoke(ctx, eventOfSize(150)),
                    "an oversized record must fail typed at the queue entry");
            assertTrue(e.getCause() instanceof StreamException,
                    "the rejection must be typed, got " + e.getCause());
            StreamException se = (StreamException) e.getCause();
            assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), se.getErrorCode());
            String detail = String.valueOf(se.getMessage()) + se;
            assertTrue(detail.contains("maxRecordBytes"),
                    "the failure must name the envelope: " + detail);
            assertEquals(1, queue.size(), "the rejected record must NOT pollute the queue");
        } finally {
            reader.close();
        }
    }

    @Test
    void nonPositiveMaxRecordBytesFailsFastR403() {
        assertThrows(StreamException.class,
                () -> new DebeziumCdcSourceReader(newConfig(), null, null, 0L),
                "maxRecordBytes <= 0 must fail fast typed");
        StreamException e = assertThrows(StreamException.class,
                () -> new DebeziumCdcSourceReader(newConfig(), null, null, -1L),
                "negative maxRecordBytes must fail fast typed");
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }
}
