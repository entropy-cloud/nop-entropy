/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorDirection;
import io.nop.stream.core.connector.registry.ConnectorSampleResult;
import io.nop.stream.core.connector.registry.IStreamConnectorFactory;
import io.nop.stream.core.connector.registry.IStreamSinkFactory;
import io.nop.stream.core.connector.registry.IStreamSourceFunctionFactory;
import io.nop.stream.core.connector.registry.IStreamSplitSourceFactory;
import io.nop.stream.core.connector.registry.StreamConnectorCatalog;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.source.Source;

import java.util.List;

/**
 * WI4 (plan 2329): driver behind the {@code sample} maintenance command — the data
 * sibling of {@link StreamConnectivityProber}. Resolves the factory through the
 * registry, constructs the endpoint, and dispatches on one closed question: does the
 * endpoint implement {@link SampleableSource}?
 *
 * <ul>
 *   <li>yes — the endpoint produces up to {@code limit} records (the endpoint owns the
 *       read-only side-effect budget);</li>
 *   <li>no — an explicit SKIP result with the endpoint class and contract reason. An
 *       empty record list is never reported as a silent success for unsupported
 *       endpoints (no-silent-no-op rule).</li>
 * </ul>
 *
 * The constructed endpoint is closed when {@code AutoCloseable} (same lifecycle as the
 * catalog probes).
 */
public final class StreamDataSampler {

    private StreamDataSampler() {
    }

    public static ConnectorSampleResult sample(StreamConnectorCatalog catalog, String typeName,
                                               StreamConnectorConfig config, int limit) {
        IStreamConnectorFactory factory = catalog.getRegistry().resolveSourceFactory(typeName);
        ConnectorCapabilityDescriptor descriptor = factory.describeCapabilities();
        if (descriptor.getDirection() != ConnectorDirection.SOURCE) {
            throw new IllegalArgumentException("sample dispatch requires a SOURCE endpoint: " + typeName);
        }
        Object endpoint = construct(factory, config);
        try {
            if (endpoint instanceof SampleableSource sampleable) {
                List<Object> records = sampleable.sample(limit);
                return ConnectorSampleResult.sampled(typeName, endpoint.getClass().getName(), records);
            }
            return ConnectorSampleResult.skipped(typeName, endpoint.getClass().getName(),
                    NopStreamErrors.ERR_STREAM_SAMPLE_NOT_SUPPORTED.getErrorCode(),
                    "endpoint implements no SampleableSource contract (sinks, message, debezium "
                            + "and batch-loader families keep their connectivity-probe red lines: "
                            + "no subscription, no embedded engine, no batch consumption)");
        } finally {
            if (endpoint instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    // mirror the catalog probe lifecycle: close failures on the sampling
                    // path surface as a wrapped failure, not a silent swallow
                    if (e instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new IllegalStateException("Failed to close sampled endpoint " + typeName, e);
                }
            }
        }
    }

    private static Object construct(IStreamConnectorFactory factory, StreamConnectorConfig config) {
        if (factory instanceof IStreamSplitSourceFactory splitFactory) {
            Source<?, ?, ?> source = splitFactory.createSource(config);
            if (source == null) {
                throw new IllegalStateException("split source factory returned null source: "
                        + factory.getTypeName());
            }
            return source;
        }
        if (factory instanceof IStreamSourceFunctionFactory functionFactory) {
            SourceFunction<?> fn = functionFactory.createSourceFunction(config);
            if (fn == null) {
                throw new IllegalStateException("source function factory returned null function: "
                        + factory.getTypeName());
            }
            return fn;
        }
        if (factory instanceof IStreamSinkFactory) {
            throw new IllegalArgumentException("sample dispatch requires a SOURCE factory, got sink: "
                    + factory.getTypeName());
        }
        throw new IllegalStateException("resolved factory implements no known source contract: "
                + factory.getClass().getName());
    }
}
