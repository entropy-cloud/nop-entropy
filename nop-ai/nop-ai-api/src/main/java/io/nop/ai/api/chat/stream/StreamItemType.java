/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.stream;

/**
 * 流式 item 增量的类型。与非流式 messages 体系同构：
 * <ul>
 *   <li>{@link #text} — 文本内容增量，收敛为 {@code ChatAssistantMessage}</li>
 *   <li>{@link #reasoning} — 推理/思考增量，收敛为 {@code ChatReasoningMessage}</li>
 *   <li>{@link #tool_call} — 工具调用增量，收敛为 {@code ChatToolCallMessage}</li>
 * </ul>
 * 设计来源：{@code ai-dev/design/nop-ai-responses-migration-design.md} §3.3。
 */
public enum StreamItemType {
    text,
    reasoning,
    tool_call
}
