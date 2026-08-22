package io.nop.ai.api.chat.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.exceptions.NopAiException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.SourceLocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P0 回归：{@link ChatStreamAccumulator} 对完整 arguments 通道（Gemini/Ollama 型
 * 单事件完整下发 name + args 的 ADDED chunk）的拼装——修复前 arguments 恒为空。
 * <p>
 * {@code ChatStreamAccumulator.toChatToolCall} 内部经 {@link JSON#parse} 解析拼装结果，
 * 该入口依赖已注册的 JSON provider（平台引导时注册）；本模块测试无引导，故注册最小
 * Jackson 适配（同 nop-core TestJsonTool 的 registerProvider 模式），用后恢复。
 */
public class TestChatStreamAccumulator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void registerJsonProvider() {
        JSON.registerProvider(new io.nop.api.core.json.IJsonProvider() {
            @Override
            public Object parseFromText(SourceLocation loc, String str,
                                         io.nop.api.core.json.JsonParseOptions options) {
                try {
                    return MAPPER.readValue(str, Object.class);
                } catch (Exception e) {
                    throw new NopAiException("jsonParseError", e);
                }
            }

            @Override
            public String stringify(Object o, Function<String, String> transformer, String indent) {
                try {
                    return MAPPER.writeValueAsString(o);
                } catch (Exception e) {
                    throw new NopAiException("jsonStringifyError", e);
                }
            }
        });
    }

    @AfterAll
    static void resetJsonProvider() {
        JSON.registerProvider(null);
    }

    @Test
    void toolCallCompleteArgumentsAssembled() {
        ChatStreamAccumulator accumulator = new ChatStreamAccumulator();

        ChatStreamChunk added = new ChatStreamChunk();
        added.setItemType(StreamItemType.tool_call);
        added.setItemIndex(0);
        added.setCallId("call_42");
        added.setPhase(StreamItemPhase.ADDED);
        added.setDelta("get_weather");
        added.setArguments("{\"location\":\"beijing\"}");

        ChatStreamChunk done = new ChatStreamChunk();
        done.setPhase(StreamItemPhase.DONE);
        done.setFinishReason("tool_calls");

        accumulator.accumulate(added);
        accumulator.accumulate(done);

        List<ChatToolCall> toolCalls = accumulator.getAccumulatedToolCalls();
        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.size());
        assertEquals("call_42", toolCalls.get(0).getId());
        assertEquals("get_weather", toolCalls.get(0).getName());
        assertEquals("beijing", toolCalls.get(0).getArguments().get("location"),
                "complete arguments channel must be assembled into the tool call");
    }

    @Test
    void toolCallCompleteArgumentsCombinedWithFragments() {
        // 完整通道与 DELTA 片段共存时按同一拼装语义处理（互不覆盖）
        ChatStreamAccumulator accumulator = new ChatStreamAccumulator();

        ChatStreamChunk added = new ChatStreamChunk();
        added.setItemType(StreamItemType.tool_call);
        added.setItemIndex(0);
        added.setPhase(StreamItemPhase.ADDED);
        added.setDelta("get_weather");

        ChatStreamChunk withArgs = new ChatStreamChunk();
        withArgs.setItemType(StreamItemType.tool_call);
        withArgs.setItemIndex(0);
        withArgs.setPhase(StreamItemPhase.ADDED);
        withArgs.setDelta("get_weather");
        withArgs.setArguments("{\"location\":\"beijing\"");

        ChatStreamChunk fragment = new ChatStreamChunk();
        fragment.setItemType(StreamItemType.tool_call);
        fragment.setItemIndex(0);
        fragment.setPhase(StreamItemPhase.DELTA);
        fragment.setDelta(",\"unit\":\"celsius\"}");

        accumulator.accumulate(added);
        accumulator.accumulate(withArgs);
        accumulator.accumulate(fragment);

        List<ChatToolCall> toolCalls = accumulator.getAccumulatedToolCalls();
        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.size());
        assertEquals("beijing", toolCalls.get(0).getArguments().get("location"));
        assertEquals("celsius", toolCalls.get(0).getArguments().get("unit"));
    }
}
