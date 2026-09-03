/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
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
                            "input directory enumerated into file splits")))
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
        return new FileSource(config.requireString("directoryPath"));
    }
}
