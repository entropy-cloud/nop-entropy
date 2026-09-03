/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSinkFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.common.functions.SinkFunction;

import java.nio.charset.Charset;
import java.util.List;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.STRING;

/**
 * SPI factory for the {@code file} sink type: constructs the exactly-once
 * {@link FileTwoPhaseCommitSink} (item 19 / P-REQ-28). Registered as a stateless bean in
 * {@code _vfs/nop/stream/beans/connector-file.beans.xml}.
 */
public final class FileTwoPhaseCommitSinkConnectorFactory implements IStreamSinkFactory {

    public static final String TYPE_NAME = "file";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .sink(TYPE_NAME, FileTwoPhaseCommitSink.class.getName())
            .sinkConsistency(SinkConsistencyCapability.TWO_PHASE_COMMIT)
            .parallelism(ConnectorParallelism.PLANNING_GATE_PARALLELISM_1)
            .recoverySemantic(ConnectorRecoverySemantic.TWO_PHASE_PENDING_COMMITS)
            .params(List.of(
                    ConnectorParamDescriptor.required("outputDir", STRING,
                            "output directory for per-epoch committed files and manifest"),
                    ConnectorParamDescriptor.optional("charset", STRING,
                            "text charset name, defaults to UTF-8")))
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
    public SinkFunction<?> createSink(StreamConnectorConfig config) {
        String outputDir = config.requireString("outputDir");
        String charsetName = config.getString("charset");
        Charset charset = charsetName == null ? null : Charset.forName(charsetName);
        return new FileTwoPhaseCommitSink<>(outputDir, charset);
    }
}
