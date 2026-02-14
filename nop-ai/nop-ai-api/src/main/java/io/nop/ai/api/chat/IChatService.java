package io.nop.ai.api.chat;

import io.nop.ai.api.chat.stream.IChatStreamHandler;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

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
     * 流式调用（回调方式）
     * <p>
     * 实时接收 AI 的增量响应，适合需要实时展示的场景（如打字机效果）。
     * 每个增量通过 {@link IChatStreamHandler#onNext} 回调返回。
     *
     * @param request     请求
     * @param handler     流式数据处理器
     * @param cancelToken 取消令牌
     */
    void callStream(ChatRequest request, IChatStreamHandler handler, ICancelToken cancelToken);
}
