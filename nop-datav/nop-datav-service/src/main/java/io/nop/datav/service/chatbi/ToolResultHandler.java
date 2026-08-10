package io.nop.datav.service.chatbi;

import io.nop.ai.toolkit.model.AiToolCallResult;

/**
 * Tool-calling 循环中工具结果的**可插拔处理器**（裁定 L 泛化点 2）。
 *
 * <p>循环在每次成功执行工具后调用 {@link #handle}，处理器按工具名/状态决定是否把结果字段
 * 累加进 {@link ChatBiResult} 累加器。查询路径注入 query handler（解析 columns/rows），
 * 生成路径注入 dashboard handler（解析 dashboardId）。</p>
 *
 * <p>处理器应只关心「成功且匹配工具名」的结果；错误结果由循环统一回喂 LLM，不经处理器。</p>
 */
@FunctionalInterface
public interface ToolResultHandler {

    /**
     * @param toolName    工具名
     * @param result      工具执行结果
     * @param content     回喂 LLM 的文本（即 result 的 output/error body）
     * @param accumulator 结果累加器（处理器按需写入 columns/rows/createdEntityId 等字段）
     */
    void handle(String toolName, AiToolCallResult result, String content, ChatBiResult accumulator);
}
