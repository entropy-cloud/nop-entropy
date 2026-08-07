/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 助手消息，仅承载 assistant 输出文本。推理过程由 {@link ChatReasoningMessage}、
 * 工具调用由 {@link ChatToolCallMessage} 承载（plan 329 删除了寄居字段 think/toolCalls）。
 */
@DataBean
public class ChatAssistantMessage extends ChatMessage {

    /**
     * 消息内容
     */
    private String content;

    public ChatAssistantMessage() {
    }

    public ChatAssistantMessage(String content) {
        this.content = content;
    }

    @Override
    public String getRole() {
        return "assistant";
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public ChatAssistantMessage copy() {
        ChatAssistantMessage copy = new ChatAssistantMessage();
        copy.setMessageId(this.getMessageId());
        copy.setProviderHints(this.providerHints);
        copy.content = this.content;
        return copy;
    }
}
