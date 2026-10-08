/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorMaturity;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSplitSourceFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.source.Source;

import java.util.List;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.STRING;

/**
 * SPI factory for the {@code file} source type: constructs the FLIP-27 split-based
 * {@link FileSource} (item 19 / P-REQ-28). Registered as a stateless bean in
 * {@code _vfs/nop/stream/beans/connector-file.beans.xml}.
 */
public final class FileSourceConnectorFactory implements IStreamSplitSourceFactory {

    public static final String TYPE_NAME = "file";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .source(TYPE_NAME, FileSource.class.getName())
            .sourceConsistency(SourceConsistencyCapability.AT_LEAST_ONCE)
            .parallelism(ConnectorParallelism.PARALLEL)
            .recoverySemantic(ConnectorRecoverySemantic.SPLIT_CURSOR_CHECKPOINT)
            .params(List.of(
                    ConnectorParamDescriptor.required("directoryPath", STRING,
                            "input directory enumerated into file splits"),
                    // WI5 (plan 2330): optional production tuning. Descriptor INT kind
                    // checks parseability only — positivity is the construction contract
                    // (unset = 0 = channel off; explicit <= 0 rejected at the factory).
                    ConnectorParamDescriptor.optional("rateLimitRowsPerSecond",
                            io.nop.stream.core.connector.registry.ConnectorParamKind.INT,
                            "max records per second (0/unset = unlimited; WI5)"),
                    ConnectorParamDescriptor.optional("rateLimitBytesPerSecond",
                            io.nop.stream.core.connector.registry.ConnectorParamKind.INT,
                            "max bytes per second (0/unset = unlimited; WI5)"),
                    ConnectorParamDescriptor.optional("splitAssignmentPolicy", STRING,
                            "split assignment policy (WI5)",
                            false, List.of(FileSplitEnumerator.POLICY_ROUND_ROBIN,
                                    FileSplitEnumerator.POLICY_SIZE_AWARE), null)))
            .maturity(ConnectorMaturity.PRODUCTION)
            .build();

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public ConnectorCapabilityDescriptor describeCapabilities() {
        return DESCRIPTOR;
    }

    @Override
    public Source<?, ?, ?> createSource(StreamConnectorConfig config) {
        // WI5: unset optional params -> 0 (channel off); explicitly non-positive values
        // are rejected HERE (the factory can distinguish unset from explicit via the
        // null config value; FileSource cannot).
        long rows = readPositiveOrZero(config, "rateLimitRowsPerSecond");
        long bytes = readPositiveOrZero(config, "rateLimitBytesPerSecond");
        String policy = config.getString("splitAssignmentPolicy");
        return new FileSource(config.requireString("directoryPath"),
                FileSourceReader.DEFAULT_MAX_LINE_BYTES, rows, bytes,
                policy == null ? FileSplitEnumerator.POLICY_ROUND_ROBIN : policy);
    }

    private static long readPositiveOrZero(StreamConnectorConfig config, String name) {
        Integer value = config.getInt(name);
        if (value == null) {
            return 0L;
        }
        if (value <= 0) {
            throw new io.nop.stream.core.exceptions.StreamException(
                    io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG)
                    .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                            name + " must be positive when set (got " + value
                                    + "); leave it unset for unlimited");
        }
        return value;
    }
}
