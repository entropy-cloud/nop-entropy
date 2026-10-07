/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.kafka;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorMaturity;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSinkFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.OBJECT;
import static io.nop.stream.core.connector.registry.ConnectorParamKind.STRING;

/**
 * SPI factory for the native {@code kafka-2pc} sink type (WI3): constructs the
 * transactional {@link KafkaTwoPhaseCommitSink} (commit-at-barrier epoch transactions).
 * Value serializer / key extractor are programmatic OBJECT params. Registered in
 * {@code _vfs/nop/stream/beans/connector-kafka.beans.xml}.
 */
public final class KafkaTwoPhaseCommitSinkConnectorFactory implements IStreamSinkFactory {

    public static final String TYPE_NAME = "kafka-2pc";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .sink(TYPE_NAME, KafkaTwoPhaseCommitSink.class.getName())
            .sinkConsistency(SinkConsistencyCapability.TWO_PHASE_COMMIT)
            .parallelism(ConnectorParallelism.PARALLEL)
            .recoverySemantic(ConnectorRecoverySemantic.TWO_PHASE_PENDING_COMMITS)
            .params(List.of(
                    ConnectorParamDescriptor.required("bootstrapServers", STRING,
                            "Kafka bootstrap servers list"),
                    ConnectorParamDescriptor.required("topic", STRING,
                            "target topic"),
                    ConnectorParamDescriptor.required("transactionalIdPrefix", STRING,
                            "transactional.id prefix; the runtime appends |jobId|vertexId|taskIndex — "
                                    + "two jobs must not share a prefix"),
                    ConnectorParamDescriptor.optional("producerProps", OBJECT,
                            "additional KafkaProducer properties (Map<String,Object>); "
                                    + "transactional.id override is rejected"),
                    ConnectorParamDescriptor.optional("valueSerializer", OBJECT,
                            "Function<T,byte[]> record value serializer, supplied programmatically "
                                    + "(default UTF-8 bytes of toString)"),
                    ConnectorParamDescriptor.optional("keyExtractor", OBJECT,
                            "Function<T,String> record key extractor; omit for null-key records")))
            .maturity(ConnectorMaturity.EXPERIMENTAL)
            .build();

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public ConnectorCapabilityDescriptor describeCapabilities() {
        return DESCRIPTOR;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SinkFunction<?> createSink(StreamConnectorConfig config) {
        String bootstrapServers = config.requireString("bootstrapServers");
        String topic = config.requireString("topic");
        String transactionalIdPrefix = config.requireString("transactionalIdPrefix");
        Map<String, Object> producerProps = config.getObject("producerProps", Map.class);
        Function<Object, byte[]> serializer = config.getObject("valueSerializer", Function.class);
        Function<Object, String> keyExtractor = config.getObject("keyExtractor", Function.class);

        Function<Object, byte[]> actualSerializer = serializer != null
                ? serializer
                : value -> String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return new KafkaTwoPhaseCommitSink<>(bootstrapServers, topic, transactionalIdPrefix, producerProps,
                actualSerializer, keyExtractor, transactionalIdPrefix);
    }
}
