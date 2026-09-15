package io.nop.ai.core.commons.processor;

import io.nop.ai.core.api.messages.AiChatExchange;

/**
 * Predicate used to validate a legacy chat response, e.g. to decide whether it passes
 * a content or format check before being accepted.
 *
 * <p><b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 零
 * import），legacy chat 管线的响应校验契约预留面。保留为公共 API 预留；删除需单独 plan + 迁移
 * 评估。
 */
@FunctionalInterface
public interface IAiChatResponseChecker {
    /**
     * @param chatResponse the legacy chat response to check
     * @return true if the response is accepted
     */
    boolean isAccepted(AiChatExchange chatResponse);
}
