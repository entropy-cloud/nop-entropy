/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.stream;

/**
 * 流式响应回调处理器
 * <p>
 * 用于处理 AI 流式响应的事件回调
 */
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

    /**
     * 组合另一个处理器，形成链式处理
     * <p>
     * 执行顺序：先执行当前处理器，再执行另一个处理器。
     * 如果当前处理器抛出异常，另一个处理器仍然会被执行（错误会传播）。
     *
     * <p>用法示例：
     * <pre>
     * // 累积器 + 实时输出
     * IChatStreamHandler handler = accumulator.asHandler()
     *     .compose(IChatStreamHandler.of(System.out::print));
     *
     * // 多个处理器链
     * IChatStreamHandler handler = loggerHandler
     *     .compose(metricsHandler)
     *     .compose(uiUpdateHandler);
     * </pre>
     *
     * @param after 后执行的处理器
     * @return 组合后的处理器
     */
    default IChatStreamHandler compose(IChatStreamHandler after) {
        return new IChatStreamHandler() {
            @Override
            public void onNext(ChatStreamChunk chunk) {
                IChatStreamHandler.this.onNext(chunk);
                after.onNext(chunk);
            }

            @Override
            public void onError(Throwable error) {
                try {
                    IChatStreamHandler.this.onError(error);
                } finally {
                    after.onError(error);
                }
            }
        };
    }

    /**
     * 创建一个简单的处理器（仅处理文本内容）
     *
     * @param contentConsumer 内容消费者
     * @return 简单处理器
     */
    static IChatStreamHandler of(java.util.function.Consumer<String> contentConsumer) {
        return new IChatStreamHandler() {
            @Override
            public void onNext(ChatStreamChunk chunk) {
                if (chunk.hasContent()) {
                    contentConsumer.accept(chunk.getContent());
                }
            }

            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
            }
        };
    }
}
