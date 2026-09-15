package io.nop.ai.core.commons.aggregator;

import io.nop.ai.core.api.messages.AiChatExchange;

import java.util.List;

/**
 * <b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 零
 * import），与 legacy chat 管线（{@code IAiChatService} 等）同期的聚合契约预留面。保留为公共
 * API 预留；删除需单独 plan + 迁移评估。与 {@code ChatStreamAccumulator}（nop-ai-api，reserved）
 * 的预留姿态一致。
 */
public interface IAiTextAggregator {
    String aggregate(List<AiChatExchange> messages);
}