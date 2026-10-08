/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import java.io.Serializable;
import java.util.List;

/**
 * WI4 (plan 2329): outcome of one {@code StreamDataSampler} dispatch — either real
 * sampled records from a {@link io.nop.stream.core.connector.SampleableSource} endpoint
 * or an explicit SKIP with the contract reason. The two states are mutually exclusive;
 * a SKIP never carries an empty record list disguised as success.
 */
public final class ConnectorSampleResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String typeName;
    private final String endpointClass;
    private final List<Object> records;
    private final String skipErrorCode;
    private final String skipDetail;

    private ConnectorSampleResult(String typeName, String endpointClass, List<Object> records,
                                  String skipErrorCode, String skipDetail) {
        this.typeName = typeName;
        this.endpointClass = endpointClass;
        this.records = records;
        this.skipErrorCode = skipErrorCode;
        this.skipDetail = skipDetail;
    }

    public static ConnectorSampleResult sampled(String typeName, String endpointClass, List<Object> records) {
        return new ConnectorSampleResult(typeName, endpointClass,
                records == null ? List.of() : List.copyOf(records), null, null);
    }

    public static ConnectorSampleResult skipped(String typeName, String endpointClass,
                                                String skipErrorCode, String skipDetail) {
        return new ConnectorSampleResult(typeName, endpointClass, null, skipErrorCode, skipDetail);
    }

    public String getTypeName() {
        return typeName;
    }

    public String getEndpointClass() {
        return endpointClass;
    }

    /** Sampled records; non-null only for the supported state. */
    public List<Object> getRecords() {
        return records;
    }

    public boolean isSupported() {
        return records != null;
    }

    /** Machine-readable SKIP code; null for the supported state. */
    public String getSkipErrorCode() {
        return skipErrorCode;
    }

    public String getSkipDetail() {
        return skipDetail;
    }
}
