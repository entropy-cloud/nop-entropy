/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import java.util.ArrayList;
import java.util.List;

public interface IMessageConsumer {

    /**
     * 处理消息并返回结果
     *
     * @param topic   来源topic
     * @param message 接收到的消息
     * @param context 事务模式下，持有事务相关信息
     * @return 如果返回null, 则表示处理完毕，可以标记消息为acknowledged。
     * 如果返回的是{@link java.util.concurrent.CompletionStage}，则表示正在异步处理，需要等待异步处理完毕之后再判断
     * 如果返回{@link ConsumeLater}，则表示标记消息为延迟处理。
     * 如果返回为其他不为null的消息，则表示需要作为响应消息返回。
     */
    Object onMessage(String topic, Object message, IMessageConsumeContext context);

    /**
     * 支持批量消息消费。批量消费可以做一些整体的性能优化工作。
     */
    default List<Object> onMessageBatch(List<TopicMessage> msgList, IMessageConsumeContext context) {
        List<Object> responses = new ArrayList<>(msgList.size());

        for (TopicMessage msg : msgList) {
            Object response = onMessage(msg.getTopic(), msg.getMessage(), context);
            responses.add(response);
        }
        return responses;
    }

    /**
     * Dispatched by the consume loop for every consumer failure, with full context
     * (D6 layer one, WI16). The default implementation logs a WARN with the complete
     * context (error-handling evidence rule) and then bridges to
     * {@link #onException(Exception)} for legacy overrides.
     *
     * <p>Override semantics: overriding this method takes over completely — the default
     * body (WARN + bridge) no longer runs and {@link #onException(Exception)} will not be
     * called. {@code onException} is kept deprecated solely for subclasses written against
     * the old signature; overriding both is not supported (this method wins).
     */
    default void onConsumeFailure(ConsumeFailure failure) {
        Exception e = failure.getThrowable();
        if (e instanceof java.lang.InterruptedException) {
            Thread.currentThread().interrupt();
        }
        org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IMessageConsumer.class);
        if (LOG.isWarnEnabled()) {
            LOG.warn("nop.err.message.consume-failure:topic={},location={},attempt={}",
                    failure.getTopic(), failure.getLocation(), failure.getAttempt(), e);
        }
        onException(e);
    }

    /**
     * @deprecated WI16 (F-20): replaced by {@link #onConsumeFailure(ConsumeFailure)} which
     * carries topic/location/message/attempt context. Kept as a bridge target for legacy
     * overrides; the default implementation here is empty because the bridge caller has
     * already logged the full context.
     */
    @Deprecated
    default void onException(Exception e){

    }
}