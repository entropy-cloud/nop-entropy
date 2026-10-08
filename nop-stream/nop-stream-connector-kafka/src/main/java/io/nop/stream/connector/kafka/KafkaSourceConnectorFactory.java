/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.kafka;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorMaturity;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSourceFunctionFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.exceptions.StreamException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.OBJECT;
import static io.nop.stream.core.connector.registry.ConnectorParamKind.STRING;

/**
 * SPI factory for the native {@code kafka} source type (WI3): constructs the
 * offset-checkpointed {@link KafkaSourceFunction}. The value deserializer is a
 * programmatic OBJECT param. Registered in
 * {@code _vfs/nop/stream/beans/connector-kafka.beans.xml}.
 *
 * <p>Maturity = PRODUCTION per the WI4 semantic-strength criterion (offset-in-checkpoint
 * recovery is fully engine-backed). The paired {@code kafka-2pc} sink stays EXPERIMENTAL:
 * its commit-at-barrier design carries a documented residual duplicate window, so its
 * guarantee is not yet fully engine-backed.
 */
public final class KafkaSourceConnectorFactory implements IStreamSourceFunctionFactory {

    public static final String TYPE_NAME = "kafka";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .source(TYPE_NAME, KafkaSourceFunction.class.getName())
            .sourceConsistency(SourceConsistencyCapability.REPLAYABLE)
            .parallelism(ConnectorParallelism.PARALLEL)
            .recoverySemantic(ConnectorRecoverySemantic.OFFSET_CHECKPOINT)
            .params(List.of(
                    ConnectorParamDescriptor.required("bootstrapServers", STRING,
                            "Kafka bootstrap servers list"),
                    ConnectorParamDescriptor.required("topic", STRING,
                            "source topic (manual exclusive partition assignment, no consumer group)"),
                    ConnectorParamDescriptor.optional("consumerProps", OBJECT,
                            "additional KafkaConsumer properties (Map<String,Object>); "
                                    + "enable.auto.commit=true is rejected"),
                    ConnectorParamDescriptor.optional("valueDeserializer", OBJECT,
                            "Function<byte[],T> record value deserializer, supplied programmatically "
                                    + "(default UTF-8 String)"),
                    ConnectorParamDescriptor.optional("startFrom", STRING,
                            "cold-start position: earliest (default) | latest — restored jobs always "
                                    + "resume from checkpointed offsets", false,
                            List.of("earliest", "latest"), null),
                    ConnectorParamDescriptor.optional("rateLimitRowsPerSecond", STRING,
                            "poll-batch rows-per-second throttle (0/default = unlimited; negative "
                                    + "rejected typed; rate limiting IS backpressure — poll blocks)"),
                    ConnectorParamDescriptor.optional("rateLimitBytesPerSecond", STRING,
                            "poll-batch bytes-per-second throttle (0/default = unlimited)")))
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

    @SuppressWarnings("unchecked")
    @Override
    public SourceFunction<?> createSourceFunction(StreamConnectorConfig config) {
        String bootstrapServers = config.requireString("bootstrapServers");
        String topic = config.requireString("topic");
        Map<String, Object> consumerProps = config.getObject("consumerProps", Map.class);
        Function<byte[], Object> deserializer = config.getObject("valueDeserializer", Function.class);
        String startFrom = config.getString("startFrom");

        Function<byte[], Object> actualDeserializer = deserializer != null
                ? deserializer
                : (Function<byte[], Object>) (Function<?, ?>) KafkaSourceFunction.utf8String();
        long rateRows = readPositiveOrZero(config, "rateLimitRowsPerSecond");
        long rateBytes = readPositiveOrZero(config, "rateLimitBytesPerSecond");
        return new KafkaSourceFunction<>(bootstrapServers, topic, consumerProps, actualDeserializer,
                startFrom == null ? KafkaSourceFunction.START_EARLIEST : startFrom, 1, 0)
                .withRateLimit(rateRows, rateBytes);
    }

    private long readPositiveOrZero(StreamConnectorConfig config, String name) {
        String raw = config.getString(name);
        if (raw == null || raw.isEmpty()) {
            return 0L;
        }
        long v = Long.parseLong(raw);
        if (v < 0) {
            throw new StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG)
                    .param("argName", name)
                    .param("detail", name + " must not be negative, got: " + v);
        }
        return v;
    }
}
