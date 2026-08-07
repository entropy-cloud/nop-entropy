/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.stream;

/**
 * 流式 item 增量的生命周期阶段（三段式）：
 * <ul>
 *   <li>{@link #ADDED} — item 首次声明（建槽位）。对 {@link StreamItemType#tool_call}，
 *       {@code delta} 承载函数名，{@code callId} 为工具调用 id</li>
 *   <li>{@link #DELTA} — 增量累加。对 text/reasoning，{@code delta} 为内容片段；
 *       对 {@code tool_call}，{@code delta} 为 arguments JSON 片段</li>
 *   <li>{@link #DONE} — item 结束 / 流终止，通常携带 {@code finishReason}</li>
 * </ul>
 * 设计来源：{@code ai-dev/design/nop-ai-responses-migration-design.md} §3.3。
 */
public enum StreamItemPhase {
    ADDED,
    DELTA,
    DONE
}
