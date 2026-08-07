package io.nop.ai.core.dialect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 325 test：{@link AbstractLlmDialect#normalizeFinishReason(String)} 的 Responses API
 * 扩展（{@code completed → stop}、{@code incomplete → length}）与既有映射的回归保护。
 * <p>
 * 由于目标方法是 {@code protected}，这里通过同包测试子类 {@link ProbeDialect} 暴露它做白盒断言。
 */
public class TestNormalizeFinishReason {

    static class ProbeDialect extends AbstractLlmDialect {
        public String normalize(String reason) {
            return normalizeFinishReason(reason);
        }
    }

    private final ProbeDialect dialect = new ProbeDialect();

    @Test
    void completedMapsToStop() {
        assertEquals("stop", dialect.normalize("completed"),
                "Responses API 'completed' 必须归一为 stop");
    }

    @Test
    void incompleteMapsToLength() {
        assertEquals("length", dialect.normalize("incomplete"),
                "Responses API 'incomplete' 必须归一为 length");
    }

    @Test
    void completedCaseInsensitive() {
        assertEquals("stop", dialect.normalize("COMPLETED"));
        assertEquals("stop", dialect.normalize("Completed"));
    }

    @Test
    void existingStopMappingsUnchanged() {
        assertEquals("stop", dialect.normalize("stop"));
        assertEquals("stop", dialect.normalize("end_turn"));
        assertEquals("stop", dialect.normalize("stop_sequence"));
    }

    @Test
    void existingLengthMappingsUnchanged() {
        assertEquals("length", dialect.normalize("length"));
        assertEquals("length", dialect.normalize("max_tokens"));
    }

    @Test
    void toolCallsAndContentFilterUnchanged() {
        assertEquals("tool_calls", dialect.normalize("tool_calls"));
        assertEquals("tool_calls", dialect.normalize("function_call"));
        assertEquals("content_filter", dialect.normalize("content_filter"));
        assertEquals("content_filter", dialect.normalize("safety"));
        assertEquals("content_filter", dialect.normalize("recitation"));
    }

    @Test
    void emptyOrNullReturnsNull() {
        assertNull(dialect.normalize(null));
        assertNull(dialect.normalize(""));
    }

    @Test
    void unknownReasonReturnedAsIs() {
        assertEquals("weird_reason", dialect.normalize("weird_reason"));
    }
}
