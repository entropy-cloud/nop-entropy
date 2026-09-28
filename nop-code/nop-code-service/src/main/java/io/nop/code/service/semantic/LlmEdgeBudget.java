package io.nop.code.service.semantic;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * N7.1 成本预算：限制单个项目 LLM 语义边提取的总 token 消耗。
 * 线程安全（AtomicInteger），超限后 tryConsume 返回 false。
 */
public class LlmEdgeBudget {
    private final int maxTokens;
    private final AtomicInteger consumed = new AtomicInteger(0);

    public LlmEdgeBudget(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean tryConsume(int estimatedTokens) {
        int current = consumed.get();
        if (current + estimatedTokens > maxTokens) {
            return false;
        }
        return consumed.addAndGet(estimatedTokens) <= maxTokens;
    }

    public int remaining() {
        return Math.max(0, maxTokens - consumed.get());
    }

    public int consumed() {
        return consumed.get();
    }
}
