package io.nop.gateway.core.interceptor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C8-003（plan 2551 裁定 G）钉：聚合缓冲字节上界。
 * pin-a：content 超限截断 + 截断标记（变异红形态：无界 StringBuilder 追加后长度持续增长）；
 * pin-b：per-tool-call argumentsBuilder 同键受界；
 * pin-c：小上限下多段累积行为正确（截断一次性、总长不超 maxBytes+标记长）。
 */
public class TestDeltaAccumulatorBound {

    private static int len(OpenAiDeltaAccumulator acc, String field) throws Exception {
        Field f = OpenAiDeltaAccumulator.class.getDeclaredField(field);
        f.setAccessible(true);
        Object v = f.get(acc);
        return v instanceof StringBuilder ? ((StringBuilder) v).length() : -1;
    }

    private static Map<String, Object> deltaChunk(String content) {
        return Map.of("choices", java.util.List.of(
                Map.of("delta", Map.of("content", content))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolCallChunk(int index, String args) {
        return Map.of("choices", java.util.List.of(Map.of("delta", Map.of("tool_calls",
                java.util.List.of(Map.of("index", index,
                        "function", Map.of("arguments", args)))))));
    }

    @Test
    public void pin_a_contentTruncatedAtBound() {
        OpenAiDeltaAccumulator acc = new OpenAiDeltaAccumulator(4096);
        // 灌入超界内容
        for (int i = 0; i < 100; i++) {
            acc.accumulate(deltaChunk("x".repeat(100)));
        }
        Map<String, Object> choice = (Map<String, Object>) ((java.util.List<?>) acc.toMap().get("choices")).get(0);
        Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
        String contentStr = (String) delta.get("content");
        assertTrue(contentStr.endsWith(OpenAiDeltaAccumulator.TRUNCATION_MARKER),
                "overflowed content must carry truncation marker");
        assertTrue(contentStr.length() <= 4096 + OpenAiDeltaAccumulator.TRUNCATION_MARKER.length(),
                "bounded content must not exceed maxBytes + marker");
    }

    @Test
    public void pin_b_toolCallArgumentsBounded() throws Exception {
        OpenAiDeltaAccumulator acc = new OpenAiDeltaAccumulator(4096);
        for (int i = 0; i < 100; i++) {
            acc.accumulate(toolCallChunk(0, "y".repeat(100)));
        }
        int argsLen = -1;
        Field f = OpenAiDeltaAccumulator.class.getDeclaredField("toolCallAccumulators");
        f.setAccessible(true);
        Map<Integer, ?> accs = (Map<Integer, ?>) f.get(acc);
        Object toolAcc = accs.get(0);
        Field argsField = toolAcc.getClass().getDeclaredField("argumentsBuilder");
        argsField.setAccessible(true);
        argsLen = ((StringBuilder) argsField.get(toolAcc)).length();
        assertTrue(argsLen <= 4096 + OpenAiDeltaAccumulator.TRUNCATION_MARKER.length(),
                "per-tool-call arguments must be bounded: " + argsLen);
    }

    @Test
    public void pin_c_smallBoundAccumulationConsistent() {
        OpenAiDeltaAccumulator acc = new OpenAiDeltaAccumulator(1200); // 测试最小下界 1024 之上
        for (int i = 0; i < 50; i++) {
            acc.accumulate(deltaChunk("abcd".repeat(10))); // 40/段
        }
        // 截断一次性：总长被钳制（不是每段截断标记重复拼接）
        try {
            int len = len(acc, "contentBuilder");
            assertTrue(len <= 1200 + OpenAiDeltaAccumulator.TRUNCATION_MARKER.length(),
                    "contentBuilder must be clamped: " + len);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
