/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.RoundRobinSupplier;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.nosql.core.config.RedisConfig;
import io.nop.nosql.lettuce.IRedisConnectionProvider;
import io.nop.nosql.lettuce.codec.PrefixTextCodec;

import jakarta.inject.Inject;
import java.time.Duration;

public class LettuceRedisConnectionProvider extends LifeCycleSupport
        implements IRedisConnectionProvider, IConfigRefreshable {
    private RedisConfig config;

    private RedisCodec<String, Object> codec = new PrefixTextCodec();
    private RedisClusterClient client;

    private RoundRobinSupplier<StatefulRedisClusterConnection<String, Object>> connectionSupplier;

    @Inject
    public void setConfig(RedisConfig config) {
        this.config = config;
    }

    public void setCodec(RedisCodec<String, Object> codec) {
        this.codec = codec;
    }

    public StatefulRedisClusterConnection<String, Object> getConnection() {
        return connectionSupplier.get();
    }

    @Override
    public void refreshConfig() {
        if (connectionSupplier != null) {
            int n = config.getConnectionPoolSize();
            if (n <= 0) {
                n = 1;
            }
            connectionSupplier.resize(n);
        }
    }

    @Override
    protected void doStart() {
        Guard.notNull(config, "redisConfig");

        int n = config.getConnectionPoolSize();
        if (n <= 0)
            n = 1;

        client = RedisClusterClient.create(buildClientResources(), buildRedisURI());
        client.setOptions(buildOptions());

        this.connectionSupplier = new RoundRobinSupplier<>(() -> client.connect(codec), n);
    }

    @Override
    protected void doStop() {
        if (client != null)
            client.shutdown();
    }

    private ClientResources buildClientResources() {
        return DefaultClientResources.create();
    }

    private RedisURI buildRedisURI() {
        RedisURI.Builder builder = RedisURI.builder();
        if (config.getUsername() != null && config.getPassword() != null) {
            builder.withAuthentication(config.getUsername(), config.getPassword().toCharArray());
        } else if (config.getPassword() != null) {
            builder.withPassword(config.getPassword().toCharArray());
        }
        if (config.getClientName() != null) {
            builder.withClientName(config.getClientName());
        }
        if (config.getMasterName() != null) {
            builder.withSentinelMasterId(config.getMasterName());
        }
        builder.withDatabase(config.getDatabase());
        if (config.getHost() != null) {
            builder.withHost(config.getHost());
        }
        builder.withPort(config.getPort());

        return builder.build();
    }

    private ClusterClientOptions buildOptions() {
        ClusterClientOptions.Builder builder = ClusterClientOptions.builder();
        builder.autoReconnect(true).maxRedirects(config.getMaxRedirections());

        SocketOptions socketOptions = buildSocketOptions();
        builder.socketOptions(socketOptions);

        TimeoutOptions timeoutOptions = buildTimeoutOptions();
        if (timeoutOptions != null) {
            builder.timeoutOptions(timeoutOptions);
        }
        builder.validateClusterNodeMembership(true);
        return builder.build();
    }

    private SocketOptions buildSocketOptions() {
        return SocketOptions.builder().connectTimeout(Duration.ofMillis(config.getConnectionTimeout())).keepAlive(true)
                .tcpNoDelay(true).build();
    }

    private TimeoutOptions buildTimeoutOptions() {
        if (config.getSoTimeout() <= 0)
            return null;

        return TimeoutOptions.builder().timeoutCommands(true).fixedTimeout(Duration.ofMillis(config.getSoTimeout()))
                .build();
    }
}