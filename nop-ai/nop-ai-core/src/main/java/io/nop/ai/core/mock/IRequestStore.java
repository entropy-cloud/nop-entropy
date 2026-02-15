/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.mock;

import io.nop.ai.api.chat.ChatRequest;

/**
 * 请求存储接口。
 * 负责保存请求信息，便于调试和手动构造响应。
 */
public interface IRequestStore {

    /**
     * 保存请求信息。
     *
     * @param request 聊天请求
     * @return 返回此请求的唯一标识，可用于构造响应文件路径
     */
    String saveRequest(ChatRequest request);

    /**
     * 获取存储的请求提示词（最后一条用户消息）。
     *
     * @param requestId 请求ID
     * @return 提示词内容
     */
    String getRequestPrompt(String requestId);
}
