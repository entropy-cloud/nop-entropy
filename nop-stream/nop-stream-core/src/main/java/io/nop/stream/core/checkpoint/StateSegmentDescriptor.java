/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Describes one physical segment that backs an EpochManifest. For Stage 31
 * incremental checkpoints each segment is a content-addressed RocksDB SST file.
 */
@DataBean
public class StateSegmentDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---- segmentType values ----

    /** A content-addressed RocksDB SST file (Stage 31 incremental checkpoint). */
    public static final String SEGMENT_TYPE_ROCKSDB_SST = "rocksdb-sst";

    // ---- codec values (documented in checkpoint-design.md §2.6) ----

    /**
     * Segment content is a JSON-serializable state blob (the default/full-scan path).
     * Restore deserializes via {@code JsonTool}.
     */
    public static final String CODEC_JSON = "json";

    /**
     * Segment content is opaque raw bytes (an SST file); the path field is the
     * content hash (SHA-256) used to locate the file in {@code ISegmentStore}.
     * Restore copies the raw file back; no deserialization.
     */
    public static final String CODEC_IDENTITY = "identity";

    /** RocksDB-SST segment schema version: SST content hash + non-SST companion files. */
    public static final int SCHEMA_VERSION_ROCKSDB_SST = 1;

    private final String segmentType;
    private final String path;
    private final String codec;
    private final String checksum;
    private final int schemaVersion;

    public StateSegmentDescriptor(String segmentType, String path, String codec,
                                  String checksum, int schemaVersion) {
        this.segmentType = segmentType;
        this.path = path;
        this.codec = codec != null ? codec : CODEC_JSON;
        this.checksum = checksum;
        this.schemaVersion = schemaVersion;
    }

    public StateSegmentDescriptor() {
        this(null, null, CODEC_JSON, null, 1);
    }

    public String getSegmentType() {
        return segmentType;
    }

    public String getPath() {
        return path;
    }

    public String getCodec() {
        return codec;
    }

    public String getChecksum() {
        return checksum;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * Validate the codec against the known value set. Unknown codecs fail-fast on
     * restore (No Silent No-Op rule): the restore path must know how to materialize
     * the segment, and guessing would silently corrupt state.
     *
     * @throws IllegalStateException if the codec is neither {@link #CODEC_JSON} nor
     *                               {@link #CODEC_IDENTITY}
     */
    public void validateCodec() {
        if (!CODEC_JSON.equals(codec) && !CODEC_IDENTITY.equals(codec)) {
            throw new IllegalStateException(
                    "Unknown StateSegmentDescriptor codec: " + codec
                            + " (expected one of [" + CODEC_JSON + ", " + CODEC_IDENTITY + "])");
        }
    }
}
