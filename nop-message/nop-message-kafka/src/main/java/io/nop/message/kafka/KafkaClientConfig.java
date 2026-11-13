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
 * consumer. Since WI13, extraProps are merged FIRST: keys the code writes (bootstrap
 * servers, client id, group id, managed tuning keys) cannot be overridden via extraProps. {@code extraProps} carries arbitrary Kafka client
 * properties (e.g. SASL/SSL) that are merged into both producer and consumer configs.
 */
@DataBean
public class KafkaClientConfig {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(KafkaClientConfig.class);
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

    /**
     * 4.1-C1-012（plan 2558 裁定 G）：返回冻结不可变视图——调用方无法经此活引用外泄可变集合。
     * 「装配期专用」为 documented-only 契约：addExtraProperty 仍写 raw 字段（读侧视图化，无生命周期钩子）。
     */
    public Map<String, String> getExtraProps() {
        return extraProps == null ? null : Map.copyOf(extraProps);
    }

    public void setExtraProps(Map<String, String> extraProps) {
        this.extraProps = extraProps;
    }

    /**
     * 4.1-C1-012（plan 2558 裁定 G）：装配期专用写入口。null 值跳过 + WARN（Map.copyOf 视角不可含 null）。
     */
    public void addExtraProperty(String key, String value) {
        if (key == null || value == null) {
            LOG.warn("nop.warn.kafka.extra-prop-null-skip:key={},ignored=true", key);
            return;
        }
        if (extraProps == null)
            extraProps = new LinkedHashMap<>();
        extraProps.put(key, value);
    }
}
