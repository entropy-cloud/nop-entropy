/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.pubsub.RedisPubSubListener;

/**
 * PubSub 连接操作缝（plan 2518 裁定①②③测试缝）：订阅管理监视器逻辑与 lettuce 连接面解耦，
 * 并发护栏测试以 fake 链接注入（非 docker 可跑，勘误 9 口径）。
 *
 * <p>线程域硬条款（M1）：{@link #addStateListener} 的回调跑在 lettuce IO/event-loop 线程上，
 * 实现只允许把激活事件投递给服务侧（服务内部再转专用对账线程），禁止在回调内取服务监视器、
 * 发阻塞 Redis 命令或执行消费者回调。</p>
 */
interface PubSubLink extends AutoCloseable {

    /** 阻塞 SUBSCRIBE（服务监视器内调用，受命令超时预算约束——裁定①显式接受）。 */
    void subscribe(String topic);

    /** 阻塞 UNSUBSCRIBE（服务监视器内调用，受命令超时预算约束——裁定①显式接受）。 */
    void unsubscribe(String topic);

    /** 注册消息适配器（锁定内 first-listener 门下调用）。 */
    void addMessageListener(RedisPubSubListener<String, Object> entry);

    /** 注销消息适配器。 */
    void removeMessageListener(RedisPubSubListener<String, Object> entry);

    /**
     * 注册连接激活事件侦听（断线重连后 lettuce 自主 resubscribe 的可观测钩子——裁定③；
     * 回调在 lettuce IO 线程执行，仅允许投递事件）。
     */
    void addStateListener(Runnable onActivated);

    @Override
    void close();
}
