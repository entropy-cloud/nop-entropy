/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.stream;

/**
 * 流式响应回调处理器（已弃用）
 * <p>
 * 该接口已被弃用，请使用 {@link java.util.concurrent.Flow.Publisher} 和
 * {@link java.util.concurrent.Flow.Subscriber} 替代。
 *
 * @deprecated 使用 JDK 标准响应式流接口 Flow.Publisher/Flow.Subscriber 替代
 * @see java.util.concurrent.Flow.Publisher
 * @see java.util.concurrent.Flow.Subscriber
 */
@Deprecated
public interface IChatStreamHandler {

    /**
     * 收到新的流式数据块。收到finish_reason表示结束
     *
     * @param chunk 数据块，包含 content、thinking 或 toolCall 增量
     */
    void onNext(ChatStreamChunk chunk);

    /**
     * 流式响应发生错误
     *
     * @param error 错误信息
     */
    void onError(Throwable error);
}
