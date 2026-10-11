/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.mock;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

/**
 * 模拟响应提供者接口。
 * 负责等待和获取AI响应，可以有不同的实现方式（文件系统、内存、网络等）。
 */
public interface IResponseProvider {

    /**
     * 等待并获取响应。
     *
     * @param request     聊天请求
     * @param cancelToken 取消令牌
     * @return 异步返回ChatResponse
     */
    CompletionStage<ChatResponse> awaitResponse(ChatRequest request, ICancelToken cancelToken);
}
