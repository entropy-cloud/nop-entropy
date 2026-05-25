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

@DataBean
public class StateSegmentDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String segmentType;
    private final String path;
    private final String codec;
    private final String checksum;
    private final int schemaVersion;

    public StateSegmentDescriptor(String segmentType, String path, String codec,
                                  String checksum, int schemaVersion) {
        this.segmentType = segmentType;
        this.path = path;
        this.codec = codec != null ? codec : "json";
        this.checksum = checksum;
        this.schemaVersion = schemaVersion;
    }

    public StateSegmentDescriptor() {
        this(null, null, "json", null, 1);
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
}
