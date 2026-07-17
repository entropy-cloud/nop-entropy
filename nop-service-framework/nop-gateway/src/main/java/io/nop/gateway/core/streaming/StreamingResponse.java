/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.streaming;

import io.nop.gateway.model.GatewayStreamingModel;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式响应对象，包装Flow.Publisher和相关配置
 *
 * <p>此对象存储在GatewayContext中，供GatewayHttpFilter使用来执行真正的流式响应。</p>
 * <p>支持客户端断连传播：通过{@link #setUpstreamSubscription(Flow.Subscription)}注入上游
 * Subscription，客户端断连时调用{@link #abort()}取消上游推送。</p>
 */
public class StreamingResponse {

    private final Flow.Publisher<Object> publisher;
    private final String contentType;
    private final GatewayStreamingModel streamingModel;
    private final AtomicReference<Flow.Subscription> upstreamSubscription = new AtomicReference<>();

    public StreamingResponse(Flow.Publisher<Object> publisher,
                             String contentType,
                             GatewayStreamingModel streamingModel) {
        this.publisher = publisher;
        this.contentType = contentType;
        this.streamingModel = streamingModel;
    }

    /**
     * 获取流式数据发布者
     */
    public Flow.Publisher<Object> getPublisher() {
        return publisher;
    }

    /**
     * 获取Content-Type（如text/event-stream或application/x-ndjson）
     */
    public String getContentType() {
        return contentType;
    }


    /**
     * 获取流式配置模型
     */
    public GatewayStreamingModel getStreamingModel() {
        return streamingModel;
    }

    /**
     * 注入上游Subscription，用于客户端断连时取消上游推送
     */
    public void setUpstreamSubscription(Flow.Subscription subscription) {
        if (subscription == null)
            throw new IllegalArgumentException("upstreamSubscription must not be null");
        upstreamSubscription.set(subscription);
    }

    /**
     * 客户端断连时调用，取消上游推送
     */
    public void abort() {
        Flow.Subscription sub = upstreamSubscription.get();
        if (sub != null) {
            sub.cancel();
        }
    }

}
