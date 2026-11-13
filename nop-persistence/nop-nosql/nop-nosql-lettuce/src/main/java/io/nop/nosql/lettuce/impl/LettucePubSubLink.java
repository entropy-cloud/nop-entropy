/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.time.Duration;

/**
 * {@link PubSubLink} 的 lettuce 生产实现（plan 2518 裁定①②③）。
 *
 * <p>命令超时预算：连接级 {@code setTimeout(5s)}——服务监视器内的阻塞 SUBSCRIBE/UNSUBSCRIBE
 * 往返受此常量约束（裁定①锁域硬条款：监视器内阻塞往返显式接受、预算封顶）。</p>
 */
class LettucePubSubLink implements PubSubLink {

    /** 监视器内阻塞 Redis 往返的命令超时预算（裁定①缺省 5s，零新配置键）。 */
    static final Duration REDIS_COMMAND_TIMEOUT = Duration.ofSeconds(5);

    private final StatefulRedisPubSubConnection<String, Object> connection;

    LettucePubSubLink(StatefulRedisPubSubConnection<String, Object> connection) {
        this.connection = connection;
        connection.setTimeout(REDIS_COMMAND_TIMEOUT);
    }

    @Override
    public void subscribe(String topic) {
        connection.sync().subscribe(topic);
    }

    @Override
    public void unsubscribe(String topic) {
        connection.sync().unsubscribe(topic);
    }

    @Override
    public void addMessageListener(RedisPubSubListener<String, Object> entry) {
        connection.addListener(entry);
    }

    @Override
    public void removeMessageListener(RedisPubSubListener<String, Object> entry) {
        connection.removeListener(entry);
    }

    @Override
    public void addStateListener(Runnable onActivated) {
        connection.addListener(new RedisConnectionStateListener() {
            @Override
            public void onRedisConnected(RedisChannelHandler<?, ?> handler) {
                // lettuce IO/event-loop 线程：仅投递事件（M1 线程域硬条款），取监视器/发命令/跑回调均禁止
                onActivated.run();
            }
        });
    }

    @Override
    public void close() {
        connection.close();
    }
}
