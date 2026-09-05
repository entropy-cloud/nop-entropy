package io.nop.http.api.aggregator;

import io.nop.core.initialize.CoreInitialization;
import io.nop.http.api.client.DefaultServerEventResponse;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventAggregator;
import io.nop.http.api.client.IServerEventResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestStreamingAggregatorsFix {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    static IServerEventResponse event(String data) {
        DefaultServerEventResponse ret = new DefaultServerEventResponse();
        ret.setHttpStatus(200);
        ret.setData(data);
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOpenAiFinishReasonWithDeltaInSameChunk() {
        IServerEventAggregator aggregator = new OpenAIStreamingEventAggregator();
        aggregator.onNext(event("{\"id\":\"1\",\"model\":\"gpt\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"}}]}"));
        // OpenAI 终止块：delta 与 finish_reason 同时出现在同一个 choice 中
        aggregator.onNext(event("{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"length\"}]}"));
        aggregator.onNext(event("[DONE]"));

        IHttpResponse response = aggregator.getFinalResult();
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
        assertEquals("length", choices.get(0).get("finish_reason"));
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        assertEquals("Hi", message.get("content"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOllamaToolCallsAsArray() {
        IServerEventAggregator aggregator = new OllamaStreamingEventAggregator();
        aggregator.onNext(event("{\"model\":\"llama3\",\"message\":{\"role\":\"assistant\",\"content\":\"a\"}}"));
        // Ollama 的 tool_calls 是 JSON 数组
        aggregator.onNext(event("{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"function\":{\"name\":\"f\",\"arguments\":{\"a\":1}}}]}}"));
        aggregator.onNext(event("{\"done\":true,\"done_reason\":\"stop\"}"));

        IHttpResponse response = aggregator.getFinalResult();
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Map<String, Object> message = (Map<String, Object>) body.get("message");
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.size());
        assertEquals("f", ((Map<String, Object>) ((Map<String, Object>) toolCalls.get(0).get("function"))).get("name"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testClaudeThinkingDeltaAndInputTokens() {
        IServerEventAggregator aggregator = new ClaudeStreamingEventAggregator();
        aggregator.onNext(event("{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude\","
                + "\"usage\":{\"input_tokens\":7,\"output_tokens\":1}}}"));
        aggregator.onNext(event("{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}"));
        // Anthropic 扩展思考流：delta.type == thinking_delta，字段为 delta.thinking
        aggregator.onNext(event("{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"let me think\"}}"));
        aggregator.onNext(event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":12}}"));
        aggregator.onNext(event("{\"type\":\"message_stop\"}"));

        IHttpResponse response = aggregator.getFinalResult();
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Map<String, Object> content = (Map<String, Object>) body.get("content");
        assertEquals("Hi", content.get("text"));
        assertEquals("let me think", content.get("reasoning_content"));
        Map<String, Object> usage = (Map<String, Object>) body.get("usage");
        assertEquals(7, usage.get("input_tokens"));
        assertEquals(12, usage.get("output_tokens"));
        assertEquals("end_turn", body.get("stop_reason"));
    }
}
