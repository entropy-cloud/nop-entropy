/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector;

import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.common.functions.source.SourceFunction;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.43 R4-03 (plan 2536) red ⑫ bridge ① — W-12 (C8-012; adjudication C.6)
 * on the message push→pull bridge: the 256-slot queue bounds only the COUNT
 * face; the per-record byte envelope bounds the byte face at the collect
 * entry (typed reject BEFORE the put, riding the reader's existing first-error
 * channel — never a silent truncation).
 *
 * <p>Drive form (S12 = small-cap injection): an embedded stub source collects
 * oversized/small records through the reader's real runner path
 * (addSplits → start → run(QueueSourceContext) → runnerFailure → pollNext
 * typed) — the same face a real message backend drives. The white-box
 * {@code measureRecordBytes} seam pins the declared string/byte-carrier
 * direct-measure LOWER-BOUND caliber (depth-3 bounded recursion). Pre-fix red
 * evidence: the oversized leg ran against the pre-fix product (default
 * constructor existed; oversized records queued silently) and the small-cap
 * constructor / measure seam is the compile face
 * ({@code _tmp/red-2536-GD-compile.log}).
 */
class TestMessageSourceReaderByteBoundR403 {

    private static SourceFunction<String> collecting(String element) {
        return new SourceFunction<String>() {
            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect(element);
            }

            @Override
            public void cancel() {
                // no resources
            }
        };
    }

    private static MessageSourceReader<String> startedReader(SourceFunction<String> fn, long cap)
            throws Exception {
        MessageSourceReader<String> reader = new MessageSourceReader<>(fn, cap);
        reader.addSplits(List.of(new MessageTopicSplit("r403-topic", 0)));
        reader.start();
        // Give the runner thread its first scheduling slice.
        Thread.sleep(100);
        return reader;
    }

    @Test
    void oversizedRecordTypedRejectedAtCollectR403() throws Exception {
        MessageSourceReader<String> reader =
                startedReader(collecting("y".repeat(101)), 100L);
        try {
            StreamException e = assertThrows(StreamException.class, reader::pollNext,
                    "an oversized record must fail the reader typed (pre-fix: it queued "
                            + "silently and the byte face of the 256-slot bound was open)");
            assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), e.getErrorCode());
            String detail = String.valueOf(e.getMessage()) + e;
            assertTrue(detail.contains("maxRecordBytes"),
                    "the failure must name the envelope: " + detail);
        } finally {
            reader.close();
        }
    }

    @Test
    void capMinusOneRecordStillDeliveredR403() throws Exception {
        MessageSourceReader<String> reader =
                startedReader(collecting("x".repeat(99)), 100L);
        try {
            java.util.Optional<String> out = reader.pollNext();
            assertTrue(out.isPresent(), "cap-1 record must be delivered normally");
            assertEquals("x".repeat(99), out.get());
        } finally {
            reader.close();
        }
    }

    @Test
    void measureHelperIsStringByteCarrierLowerBoundR403() {
        assertEquals(8L, MessageSourceReader.measureRecordBytes(new byte[8]));
        assertEquals(5L, MessageSourceReader.measureRecordBytes("hello"));
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("a", new byte[4]);
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("k1", inner);
        outer.put("k2", "abcd");
        // depth-3 bounded recursion: outer(map) → keys+values at depth 2.
        assertEquals(2 + 1 + 4 + 2 + 4, MessageSourceReader.measureRecordBytes(outer));
        assertEquals(0L, MessageSourceReader.measureRecordBytes(new Object()));
        assertEquals(0L, MessageSourceReader.measureRecordBytes(null));
    }

    @Test
    void nonPositiveMaxRecordBytesFailsFastR403() {
        assertThrows(StreamException.class, () -> new MessageSourceReader<>(collecting("a"), 0L),
                "maxRecordBytes <= 0 must fail fast typed");
        StreamException e = assertThrows(StreamException.class,
                () -> new MessageSourceReader<>(collecting("a"), -1L),
                "negative maxRecordBytes must fail fast typed");
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }
}
