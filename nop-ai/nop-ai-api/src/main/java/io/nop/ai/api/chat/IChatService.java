package io.nop.ai.api.chat;

import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public interface IChatService {
    /**
     * 异步调用（非流式）
     */
    CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken);

    /**
     * 同步调用（非流式）
     */
    default ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
        return FutureHelper.syncGet(callAsync(request, cancelToken));
    }

    /**
     * 流式调用（响应式流方式）
     * <p>
     * 实时接收 AI 的增量响应，适合需要实时展示的场景（如打字机效果）。
     * 返回一个 {@link Flow.Publisher}，订阅者通过 {@link Flow.Subscriber} 接收数据块。
     *
     * @param request     请求
     * @param cancelToken 取消令牌
     * @return 发布者，发布 {@link ChatStreamChunk} 数据块
     */
    Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken);
}
