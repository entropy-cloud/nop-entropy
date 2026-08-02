/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 31 Phase 3: codec value set for StateSegmentDescriptor + fail-fast on
 * unknown codec (documented in checkpoint-design.md §2.6).
 */
class TestStateSegmentDescriptorCodec {

    @Test
    void defaultsToCodecJson() {
        StateSegmentDescriptor d = new StateSegmentDescriptor();
        assertEquals("json", d.getCodec());
    }

    @Test
    void nullCodecFallsBackToJson() {
        StateSegmentDescriptor d = new StateSegmentDescriptor("rocksdb-sst", "hash", null, "chk", 1);
        assertEquals("json", d.getCodec());
    }

    @Test
    void knownCodecsValidateWithoutThrowing() {
        StateSegmentDescriptor json = new StateSegmentDescriptor(
                StateSegmentDescriptor.SEGMENT_TYPE_ROCKSDB_SST, "h", StateSegmentDescriptor.CODEC_JSON, "c", 1);
        StateSegmentDescriptor identity = new StateSegmentDescriptor(
                StateSegmentDescriptor.SEGMENT_TYPE_ROCKSDB_SST, "h", StateSegmentDescriptor.CODEC_IDENTITY, "c",
                StateSegmentDescriptor.SCHEMA_VERSION_ROCKSDB_SST);
        assertDoesNotThrow(json::validateCodec);
        assertDoesNotThrow(identity::validateCodec);
        assertEquals("identity", identity.getCodec());
        assertEquals("rocksdb-sst", identity.getSegmentType());
        assertEquals(1, identity.getSchemaVersion());
    }

    @Test
    void unknownCodecFailsFast() {
        StateSegmentDescriptor d = new StateSegmentDescriptor("rocksdb-sst", "h", "made-up-codec", "c", 1);
        IllegalStateException ex = assertThrows(IllegalStateException.class, d::validateCodec);
        assertEquals("made-up-codec", d.getCodec());
        // message must mention the offending codec and the expected set (fail-fast, not silent)
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("made-up-codec"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("identity"));
    }
}
