/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

public interface IMessageSubscriber {
    /**
     * 响应消息发送到一个相关的topic上
     *
     * <p>契约：返回值必须是目标 broker 上合法的 topic 名。默认实现对「裸短名」topic
     * 加 {@code ack-} 前缀；broker 命名含域名/命名空间（如 Pulsar 全名
     * {@code persistent://tenant/ns/name}）的后端必须 override，保证对合法输入 topic
     * 派生出合法的 ack topic（MSG-F-04）。
     *
     * @param topic 请求消息所属的topic
     * @return reply消息所对应的队列
     */
    default String getAckTopic(String topic) {
        return "ack-" + topic;
    }

    IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options);

    default IMessageSubscription subscribe(String topic, IMessageConsumer listener) {
        return subscribe(topic, listener, null);
    }
}
