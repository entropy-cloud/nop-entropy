/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.annotations.data.DataBean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-level Kafka configuration (bootstrap servers + optional raw producer/consumer
 * overrides), mirroring {@code PulsarClientConfig}.
 *
 * <p>{@code bootstrapServers} is the only required field (validated non-empty in
 * {@code KafkaMessageService.init}). {@code clientId} is applied to both producer and
 * consumer unless overridden. {@code extraProps} carries arbitrary Kafka client
 * properties (e.g. SASL/SSL) that are merged into both producer and consumer configs.
 */
@DataBean
public class KafkaClientConfig {
    private String bootstrapServers;
    private String clientId;
    private Map<String, String> extraProps = new LinkedHashMap<>();

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Map<String, String> getExtraProps() {
        return extraProps;
    }

    public void setExtraProps(Map<String, String> extraProps) {
        this.extraProps = extraProps;
    }
}
