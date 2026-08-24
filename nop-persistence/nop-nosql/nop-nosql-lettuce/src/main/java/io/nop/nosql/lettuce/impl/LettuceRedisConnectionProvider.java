/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LettuceRedisConnectionProvider extends LifeCycleSupport
        implements IRedisConnectionProvider, IConfigRefreshable {
    private RedisConfig config;

    private RedisCodec<String, Object> codec = new PrefixTextCodec();
    private RedisClient standaloneClient;
    private RedisClusterClient clusterClient;
    private ClientResources clientResources;

    private RoundRobinSupplier<? extends AutoCloseable> connectionSupplier;

    @Inject
    public void setConfig(RedisConfig config) {
        this.config = config;
    }

    public void setCodec(RedisCodec<String, Object> codec) {
        this.codec = codec;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RedisClusterAsyncCommands<String, Object> getAsyncCommands() {
        checkIsActive();
        Object conn = connectionSupplier.get();
        if (conn instanceof StatefulRedisClusterConnection) {
            return ((StatefulRedisClusterConnection<String, Object>) conn).async();
        }
        return ((StatefulRedisConnection<String, Object>) conn).async();
    }

    @Override
    @SuppressWarnings("unchecked")
    public RedisClusterCommands<String, Object> getSyncCommands() {
        checkIsActive();
        Object conn = connectionSupplier.get();
        if (conn instanceof StatefulRedisClusterConnection) {
            return ((StatefulRedisClusterConnection<String, Object>) conn).sync();
        }
        return ((StatefulRedisConnection<String, Object>) conn).sync();
    }

    @SuppressWarnings("unchecked")
    public StatefulRedisConnection<String, Object> getConnection() {
        checkIsActive();
        Object conn = connectionSupplier.get();
        if (conn instanceof StatefulRedisConnection) {
            return (StatefulRedisConnection<String, Object>) conn;
        }
        throw new UnsupportedOperationException("Use getAsyncCommands()/getSyncCommands() for cluster mode");
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

        if (config.getClusterNodes() != null && !config.getClusterNodes().isEmpty()) {
            List<RedisURI> uris = buildClusterURIs();
            clientResources = DefaultClientResources.create();
            clusterClient = RedisClusterClient.create(clientResources, uris);
            clusterClient.setOptions(buildClusterOptions());
            this.connectionSupplier = new RoundRobinSupplier<>(
                    () -> clusterClient.connect(codec), n);
        } else {
            RedisURI uri = buildRedisURI();
            clientResources = DefaultClientResources.create();
            standaloneClient = RedisClient.create(clientResources, uri);
            standaloneClient.setOptions(buildStandaloneOptions());
            this.connectionSupplier = new RoundRobinSupplier<>(
                    () -> standaloneClient.connect(codec), n);
        }
    }

    @Override
    protected void doStop() {
        if (clusterClient != null)
            clusterClient.shutdown();
        if (standaloneClient != null)
            standaloneClient.shutdown();
        if (clientResources != null) {
            // shared ClientResources are not closed by client.shutdown(), shut them down explicitly
            clientResources.shutdown().awaitUninterruptibly(10, TimeUnit.SECONDS);
            clientResources = null;
        }
    }

    StatefulRedisPubSubConnection<String, Object> createPubSubConnection() {
        checkIsActive();
        if (clusterClient != null) {
            return clusterClient.connectPubSub(codec);
        }
        return standaloneClient.connectPubSub(codec);
    }

    /**
     * 解析 "host:port" 或 "[ipv6-host]:port" 形式的节点地址，格式非法时抛出带原始串的 IllegalArgumentException。
     */
    static String[] parseHostPort(String node) {
        String host = null;
        String portText = null;
        if (node.startsWith("[")) {
            int close = node.indexOf(']');
            if (close > 1 && node.length() > close + 1 && node.charAt(close + 1) == ':') {
                host = node.substring(1, close);
                portText = node.substring(close + 2);
            }
        } else {
            int idx = node.indexOf(':');
            if (idx > 0 && node.indexOf(':', idx + 1) < 0) {
                host = node.substring(0, idx);
                portText = node.substring(idx + 1);
            }
        }

        if (host == null || portText == null || portText.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid redis node address '" + node + "': expected host:port or [ipv6-host]:port");
        }

        String port = parsePort(node, portText);
        return new String[]{host.trim(), port};
    }

    private static String parsePort(String node, String portText) {
        try {
            int port = Integer.parseInt(portText.trim());
            if (port <= 0 || port > 65535)
                throw new NumberFormatException("out of range");
            return String.valueOf(port);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid redis node address '" + node + "': port must be an integer in [1,65535]");
        }
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
        boolean sentinelMode = config.getSentinelNodes() != null && !config.getSentinelNodes().isEmpty();
        if (sentinelMode) {
            // in sentinel mode the URI must not carry its own host/port, only sentinel addresses
            for (String node : config.getSentinelNodes()) {
                String[] hostPort = parseHostPort(node);
                builder.withSentinel(hostPort[0], Integer.parseInt(hostPort[1]));
            }
        } else {
            builder.withDatabase(config.getDatabase());
            if (config.getHost() != null) {
                builder.withHost(config.getHost());
            }
            builder.withPort(config.getPort());
        }
        if (config.isUseSsl()) {
            builder.withSsl(true);
            builder.withVerifyPeer(config.isVerifyPeer());
        }

        return builder.build();
    }

    private List<RedisURI> buildClusterURIs() {
        List<RedisURI> uris = new ArrayList<>();
        for (String node : config.getClusterNodes()) {
            String[] hostPort = parseHostPort(node);
            RedisURI.Builder builder = RedisURI.builder()
                    .withHost(hostPort[0])
                    .withPort(Integer.parseInt(hostPort[1]));
            if (config.getUsername() != null && config.getPassword() != null) {
                builder.withAuthentication(config.getUsername(), config.getPassword().toCharArray());
            } else if (config.getPassword() != null) {
                builder.withPassword(config.getPassword().toCharArray());
            }
            if (config.isUseSsl()) {
                builder.withSsl(true);
                builder.withVerifyPeer(config.isVerifyPeer());
            }
            uris.add(builder.build());
        }
        return uris;
    }

    private ClientOptions buildStandaloneOptions() {
        ClientOptions.Builder builder = ClientOptions.builder();
        builder.autoReconnect(true);

        SocketOptions socketOptions = buildSocketOptions();
        builder.socketOptions(socketOptions);

        TimeoutOptions timeoutOptions = buildTimeoutOptions();
        if (timeoutOptions != null) {
            builder.timeoutOptions(timeoutOptions);
        }
        return builder.build();
    }

    private ClusterClientOptions buildClusterOptions() {
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
