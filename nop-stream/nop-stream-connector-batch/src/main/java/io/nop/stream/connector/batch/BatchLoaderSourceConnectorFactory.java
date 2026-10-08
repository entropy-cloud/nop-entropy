/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorMaturity;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSplitSourceFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.source.Source;

import java.util.List;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.INT;
import static io.nop.stream.core.connector.registry.ConnectorParamKind.OBJECT;

/**
 * SPI factory for the {@code batch-loader} source type: constructs the FLIP-27
 * {@link BatchLoaderSource} bridging nop-batch loaders (item 19 / P-REQ-28; WI2 plan
 * 2343 Phase 1 moved the factory surface from the legacy
 * {@code BatchLoaderSourceFunction}, which keeps its programmatic API only).
 * The loader provider is a programmatic OBJECT param. Registered in
 * {@code _vfs/nop/stream/beans/connector-batch.beans.xml}.
 */
public final class BatchLoaderSourceConnectorFactory implements IStreamSplitSourceFactory {

    public static final String TYPE_NAME = "batch-loader";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .source(TYPE_NAME, BatchLoaderSource.class.getName())
            .sourceConsistency(SourceConsistencyCapability.AT_LEAST_ONCE)
            // R5 CON-06: align the declared capability with the runtime
            // single-writer gate (ParallelismCheckable rejects parallelism > 1)
            // and the owner-doc 单写入者 adjudication — the previous PARALLEL
            // declaration was the outlier of the three sources of truth.
            .parallelism(ConnectorParallelism.SINGLE_INSTANCE)
            .recoverySemantic(ConnectorRecoverySemantic.OFFSET_CHECKPOINT)
            .params(List.of(
                    ConnectorParamDescriptor.required("loaderProvider", OBJECT,
                            "IBatchLoaderProvider<S> supplied programmatically"),
                    ConnectorParamDescriptor.optional("batchSize", INT,
                            "records per load batch, defaults to 1")))
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
        IBatchLoaderProvider<?> loaderProvider = config.requireObject("loaderProvider", IBatchLoaderProvider.class);
        Integer batchSize = config.getInt("batchSize");
        if (batchSize != null) {
            return new BatchLoaderSource<>(loaderProvider, batchSize);
        }
        return new BatchLoaderSource<>(loaderProvider);
    }
}
