/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector;

import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorDirection;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.ConnectorSampleResult;
import io.nop.stream.core.connector.registry.IStreamSplitSourceFactory;
import io.nop.stream.core.connector.registry.StreamConnectorCatalog;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.connector.registry.StreamConnectorRegistry;
import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SourceSplit;
import io.nop.stream.core.source.SplitEnumerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 (plan 2329): sampler dispatch is a closed question — a source endpoint either
 * implements {@link SampleableSource} (records come back) or it does not (explicit
 * SKIP with the contract reason; never an empty list passing as success). The fake
 * here is a FLIP-27 Source that implements no sampling contract, the same shape as the
 * built-in message/debezium/batch-loader endpoints — every not-implementing endpoint
 * routes through this single branch.
 */
public class TestStreamDataSampler {

    @Test
    public void nonSampleableSourceIsSkippedExplicitly() {
        StreamConnectorCatalog catalog = StreamConnectorCatalog.of(
                StreamConnectorRegistry.of(List.of(new PlainSourceFactory())));
        ConnectorSampleResult result = StreamDataSampler.sample(catalog, "plain",
                new StreamConnectorConfig("plain", Map.of()), 5);
        assertFalse(result.isSupported());
        assertEquals("nop.err.stream.sample-not-supported", result.getSkipErrorCode());
        assertTrue(result.getSkipDetail().contains("SampleableSource"), result.getSkipDetail());
        assertEquals("io.nop.stream.core.connector.TestStreamDataSampler$PlainSource",
                result.getEndpointClass());
    }

    @Test
    public void sampleableSourceRecordsComeBack() {
        StreamConnectorCatalog catalog = StreamConnectorCatalog.of(
                StreamConnectorRegistry.of(List.of(new SampleableFakeFactory())));
        ConnectorSampleResult result = StreamDataSampler.sample(catalog, "fake-sample",
                new StreamConnectorConfig("fake-sample", Map.of()), 3);
        assertTrue(result.isSupported());
        assertEquals(List.of("r0", "r1", "r2"), result.getRecords());
    }

    @Test
    public void nonSourceFactoryIsRejectedTyped() {
        StreamConnectorCatalog catalog = StreamConnectorCatalog.of(
                StreamConnectorRegistry.of(List.of(new PlainSourceFactory())));
        assertThrows(Exception.class, () -> StreamDataSampler.sample(catalog, "no-such-type",
                new StreamConnectorConfig("no-such-type", Map.of()), 3));
    }

    private static final class FakeSplit implements SourceSplit {
        @Override
        public String splitId() {
            return "fake-0";
        }
    }

    /** FLIP-27 source WITHOUT the sampling contract (the SKIP branch's subject). */
    private static final class PlainSource implements Source<Object, FakeSplit, Object> {
        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<FakeSplit, Object> createEnumerator() {
            throw new UnsupportedOperationException("never enumerated in sampler tests");
        }

        @Override
        public SplitEnumerator<FakeSplit, Object> restoreEnumerator(Object checkpointState) {
            throw new UnsupportedOperationException("never restored in sampler tests");
        }

        @Override
        public SourceReader<Object, FakeSplit> createReader(SourceReaderContext context) {
            throw new UnsupportedOperationException("never read in sampler tests");
        }

        @Override
        public SimpleVersionedSerializer<Object> getEnumeratorStateSerializer() {
            throw new UnsupportedOperationException("never serialized in sampler tests");
        }

        @Override
        public SimpleVersionedSerializer<FakeSplit> getSplitSerializer() {
            throw new UnsupportedOperationException("never serialized in sampler tests");
        }
    }

    private static final class PlainSourceFactory implements IStreamSplitSourceFactory {
        private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
                .source("plain", PlainSource.class.getName())
                .sourceConsistency(io.nop.stream.core.common.functions.source.SourceConsistencyCapability.AT_LEAST_ONCE)
                .parallelism(ConnectorParallelism.PARALLEL)
                .recoverySemantic(ConnectorRecoverySemantic.NONE)
                .build();

        @Override
        public String getTypeName() {
            return "plain";
        }

        @Override
        public ConnectorCapabilityDescriptor describeCapabilities() {
            return DESCRIPTOR;
        }

        @Override
        public Source<?, ?, ?> createSource(StreamConnectorConfig config) {
            return new PlainSource();
        }
    }

    private static final class SampleableFake implements Source<Object, FakeSplit, Object>, SampleableSource {
        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<FakeSplit, Object> createEnumerator() {
            throw new UnsupportedOperationException("never enumerated in sampler tests");
        }

        @Override
        public SplitEnumerator<FakeSplit, Object> restoreEnumerator(Object checkpointState) {
            throw new UnsupportedOperationException("never restored in sampler tests");
        }

        @Override
        public SourceReader<Object, FakeSplit> createReader(SourceReaderContext context) {
            throw new UnsupportedOperationException("never read in sampler tests");
        }

        @Override
        public SimpleVersionedSerializer<Object> getEnumeratorStateSerializer() {
            throw new UnsupportedOperationException("never serialized in sampler tests");
        }

        @Override
        public SimpleVersionedSerializer<FakeSplit> getSplitSerializer() {
            throw new UnsupportedOperationException("never serialized in sampler tests");
        }

        @Override
        public List<Object> sample(int limit) {
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive: " + limit);
            }
            return java.util.stream.IntStream.range(0, Math.min(limit, 3))
                    .<Object>mapToObj(i -> "r" + i).toList();
        }
    }

    private static final class SampleableFakeFactory implements IStreamSplitSourceFactory {
        private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
                .source("fake-sample", SampleableFake.class.getName())
                .sourceConsistency(io.nop.stream.core.common.functions.source.SourceConsistencyCapability.AT_LEAST_ONCE)
                .parallelism(ConnectorParallelism.PARALLEL)
                .recoverySemantic(ConnectorRecoverySemantic.NONE)
                .params(List.of(ConnectorParamDescriptor.required("ignored",
                        io.nop.stream.core.connector.registry.ConnectorParamKind.STRING, "unused")))
                .build();

        @Override
        public String getTypeName() {
            return "fake-sample";
        }

        @Override
        public ConnectorCapabilityDescriptor describeCapabilities() {
            return DESCRIPTOR;
        }

        @Override
        public Source<?, ?, ?> createSource(StreamConnectorConfig config) {
            return new SampleableFake();
        }
    }
}
