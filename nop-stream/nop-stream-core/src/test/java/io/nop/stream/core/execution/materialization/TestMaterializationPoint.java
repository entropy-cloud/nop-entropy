/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.materialization;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

import java.util.List;

import org.junit.jupiter.api.Test;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_MATERIALIZE_POINT_SEALED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor 1: SPI + in-memory round-trip tests for the materialization
 * point mechanism (option B).
 *
 * <p>Verifies the data-plane contract: write → epoch-tagged storage → replay
 * returns the same elements in write order, filtered by epoch.
 */
public class TestMaterializationPoint {

    @Test
    public void inMemoryPointRoundTripPreservesWriteOrderAndEpoch() throws Exception {
        IMaterializationPoint point = new InMemoryMaterializationPoint("p1");

        assertEquals("p1", point.getPointId());
        assertEquals(0, point.size());
        assertEquals(-1L, point.getLastEpoch());
        assertFalse(point.isSealed());

        point.write(new StreamRecord<>("a"), 0L);
        point.write(new StreamRecord<>("b"), 0L);
        point.write(new StreamRecord<>("c"), 1L);
        point.write(new StreamRecord<>("d"), 2L);

        assertEquals(4, point.size());
        assertEquals(2L, point.getLastEpoch());

        // replayAll preserves write order
        List<MaterializedElement> all = point.replayAll();
        assertEquals(4, all.size());
        assertEquals("a", all.get(0).getElement().<String>asRecord().getValue());
        assertEquals(0L, all.get(0).getEpoch());
        assertEquals("d", all.get(3).getElement().<String>asRecord().getValue());
        assertEquals(2L, all.get(3).getEpoch());

        // replay(fromEpoch) returns only elements with epoch >= fromEpoch
        List<MaterializedElement> from1 = point.replay(1L);
        assertEquals(2, from1.size());
        assertEquals("c", from1.get(0).getElement().<String>asRecord().getValue());
        assertEquals(1L, from1.get(0).getEpoch());
        assertEquals("d", from1.get(1).getElement().<String>asRecord().getValue());

        // replay(0) == replayAll (epoch 0 is a legitimate epoch)
        assertEquals(4, point.replay(0L).size());

        // replay beyond last epoch returns empty (never null)
        assertTrue(point.replay(99L).isEmpty());
    }

    @Test
    public void inMemoryPointReplayAllReturnsDefensiveCopy() throws Exception {
        InMemoryMaterializationPoint point = new InMemoryMaterializationPoint("p1");
        point.write(new StreamRecord<>("a"), 5L);

        List<MaterializedElement> snapshot = point.replayAll();
        snapshot.clear();

        // mutating the returned list must not affect the store
        assertEquals(1, point.size());
    }

    @Test
    public void inMemoryPointHandlesWatermarksAndNonRecordElements() throws Exception {
        IMaterializationPoint point = new InMemoryMaterializationPoint("p1");
        point.write(new StreamRecord<>("record"), 0L);
        point.write(new Watermark(123L), 0L);

        List<MaterializedElement> all = point.replayAll();
        assertEquals(2, all.size());
        assertTrue(all.get(0).getElement().isRecord());
        assertTrue(all.get(1).getElement().isWatermark());
    }

    @Test
    public void sealBlocksFurtherWrites() throws Exception {
        IMaterializationPoint point = new InMemoryMaterializationPoint("p1");
        point.write(new StreamRecord<>("a"), 0L);
        assertFalse(point.isSealed());

        point.seal();
        assertTrue(point.isSealed());

        // idempotent
        point.seal();
        assertTrue(point.isSealed());

        // sealed point rejects writes (fail-fast, No-Silent-No-Op)
        StreamException ex = assertThrows(StreamException.class, () ->
                point.write(new StreamRecord<>("b"), 0L));
        assertEquals(ERR_STREAM_MATERIALIZE_POINT_SEALED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void nullPointIdRejected() {
        StreamException ex = assertThrows(StreamException.class,
                () -> new InMemoryMaterializationPoint(null));
        assertEquals(ERR_STREAM_NULL_ARG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void materializedElementRejectsNullPayload() {
        StreamException ex = assertThrows(StreamException.class,
                () -> new MaterializedElement(null, 0L));
        assertEquals(ERR_STREAM_NULL_ARG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void replayFromBeginningOnEmptyPointReturnsEmptyNotNull() {
        IMaterializationPoint point = new InMemoryMaterializationPoint("p1");
        assertNotNull(point.replayAll());
        assertTrue(point.replayAll().isEmpty());
        assertNotNull(point.replay(0L));
        assertTrue(point.replay(0L).isEmpty());
    }
}
