package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoggingInterceptorTest {

    @Test
    void nonStreaming_logsRequestAndResponse() {
        List<Object> logged = new ArrayList<>();
        IGatewayLogger logger = new IGatewayLogger() {
            @Override
            public void logRequest(ApiRequest<?> request, IGatewayContext ctx) {
                logged.add("request:" + request.getData());
            }

            @Override
            public void logResponse(ApiResponse<?> response, IGatewayContext ctx) {
                logged.add("response:" + response.getData());
            }

            @Override
            public void logStreamingResponse(Object aggregatedResponse, IGatewayContext ctx) {
                logged.add("streaming:" + aggregatedResponse);
            }

            @Override
            public void logError(Throwable exception, IGatewayContext ctx) {
                logged.add("error:" + exception.getMessage());
            }
        };

        LoggingInterceptor interceptor = new LoggingInterceptor(logger);
        IGatewayContext ctx = new GatewayContextImpl();

        ApiRequest<?> req = ApiRequest.build(Map.of("prompt", "hello"));
        ApiRequest<?> result = interceptor.onRequest(req, ctx);
        assertEquals(req, result);

        ApiResponse<?> resp = ApiResponse.success(Map.of("text", "world"));
        ApiResponse<?> respResult = interceptor.onResponse(resp, ctx);
        assertEquals(resp, respResult);

        assertEquals(2, logged.size());
        assertEquals("request:{prompt=hello}", logged.get(0));
        assertEquals("response:{text=world}", logged.get(1));
    }

    @Test
    void streaming_accumulatesDeltasAndLogsOnComplete() {
        List<Object> logged = new ArrayList<>();
        IGatewayLogger logger = new IGatewayLogger() {
            @Override
            public void logRequest(ApiRequest<?> request, IGatewayContext ctx) {
                logged.add("request");
            }

            @Override
            public void logResponse(ApiResponse<?> response, IGatewayContext ctx) {
                logged.add("response");
            }

            @Override
            public void logStreamingResponse(Object aggregatedResponse, IGatewayContext ctx) {
                logged.add(aggregatedResponse);
            }

            @Override
            public void logError(Throwable exception, IGatewayContext ctx) {
                logged.add("error:" + exception.getMessage());
            }
        };

        LoggingInterceptor interceptor = new LoggingInterceptor(logger);
        IGatewayContext ctx = new GatewayContextImpl();

        interceptor.onStreamStart(ApiRequest.build(Map.of("model", "gpt-4")), ctx);

        interceptor.onStreamElement(buildChunk("chatcmpl-1", "gpt-4", "Hello", null, null), ctx);
        interceptor.onStreamElement(buildChunk("chatcmpl-1", "gpt-4", " world", null, null), ctx);
        interceptor.onStreamElement(buildChunk("chatcmpl-1", "gpt-4", "!", "stop", null), ctx);

        interceptor.onStreamComplete(ctx);

        assertEquals(2, logged.size());
        assertEquals("request", logged.get(0));

        @SuppressWarnings("unchecked")
        Map<String, Object> aggregated = (Map<String, Object>) logged.get(1);
        assertEquals("chatcmpl-1", aggregated.get("id"));
        assertEquals("gpt-4", aggregated.get("model"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) aggregated.get("choices");
        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
        assertEquals("Hello world!", delta.get("content"));
        assertEquals("stop", choices.get(0).get("finish_reason"));
    }

    @Test
    void streaming_accumulatesToolCalls() {
        List<Object> logged = new ArrayList<>();
        IGatewayLogger logger = new IGatewayLogger() {
            @Override
            public void logRequest(ApiRequest<?> request, IGatewayContext ctx) {
                logged.add("request");
            }

            @Override
            public void logResponse(ApiResponse<?> response, IGatewayContext ctx) {
                logged.add("response");
            }

            @Override
            public void logStreamingResponse(Object aggregatedResponse, IGatewayContext ctx) {
                logged.add(aggregatedResponse);
            }

            @Override
            public void logError(Throwable exception, IGatewayContext ctx) {
                logged.add("error:" + exception.getMessage());
            }
        };

        LoggingInterceptor interceptor = new LoggingInterceptor(logger);
        IGatewayContext ctx = new GatewayContextImpl();

        interceptor.onStreamStart(ApiRequest.build(Map.of("model", "gpt-4")), ctx);

        Map<String, Object> tc1 = new LinkedHashMap<>();
        tc1.put("index", 0);
        tc1.put("id", "call_1");
        tc1.put("function", Map.of("name", "get_weather", "arguments", "{\"ci"));
        interceptor.onStreamElement(buildChunk("chatcmpl-1", "gpt-4", null, null, List.of(tc1)), ctx);

        Map<String, Object> tc2 = new LinkedHashMap<>();
        tc2.put("index", 0);
        tc2.put("function", Map.of("arguments", "ty\":\"Beijing\"}"));
        interceptor.onStreamElement(buildChunk("chatcmpl-1", "gpt-4", null, "tool_calls", List.of(tc2)), ctx);

        interceptor.onStreamComplete(ctx);

        @SuppressWarnings("unchecked")
        Map<String, Object> aggregated = (Map<String, Object>) logged.get(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) aggregated.get("choices");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) ((Map<String, Object>) choices.get(0).get("delta")).get("tool_calls");

        assertEquals(1, toolCalls.size());
        assertEquals("call_1", toolCalls.get(0).get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> func = (Map<String, Object>) toolCalls.get(0).get("function");
        assertEquals("get_weather", func.get("name"));
        assertEquals("{\"ci" + "ty\":\"Beijing\"}", func.get("arguments"));
    }

    @Test
    void onError_logsError() {
        List<Object> logged = new ArrayList<>();
        IGatewayLogger logger = new IGatewayLogger() {
            @Override
            public void logRequest(ApiRequest<?> request, IGatewayContext ctx) {
            }

            @Override
            public void logResponse(ApiResponse<?> response, IGatewayContext ctx) {
            }

            @Override
            public void logStreamingResponse(Object aggregatedResponse, IGatewayContext ctx) {
            }

            @Override
            public void logError(Throwable exception, IGatewayContext ctx) {
                logged.add("error:" + exception.getMessage());
            }
        };

        LoggingInterceptor interceptor = new LoggingInterceptor(logger);
        IGatewayContext ctx = new GatewayContextImpl();

        RuntimeException ex = new RuntimeException("timeout");
        ApiResponse<?> result = interceptor.onError(ex, ctx);

        assertNull(result);
        assertEquals(1, logged.size());
        assertEquals("error:timeout", logged.get(0));
    }

    @Test
    void onStreamError_logsError() {
        List<Object> logged = new ArrayList<>();
        IGatewayLogger logger = new IGatewayLogger() {
            @Override
            public void logRequest(ApiRequest<?> request, IGatewayContext ctx) {
            }

            @Override
            public void logResponse(ApiResponse<?> response, IGatewayContext ctx) {
            }

            @Override
            public void logStreamingResponse(Object aggregatedResponse, IGatewayContext ctx) {
            }

            @Override
            public void logError(Throwable exception, IGatewayContext ctx) {
                logged.add("stream-error:" + exception.getMessage());
            }
        };

        LoggingInterceptor interceptor = new LoggingInterceptor(logger);
        IGatewayContext ctx = new GatewayContextImpl();

        Object result = interceptor.onStreamError(new RuntimeException("connection lost"), ctx);

        assertNull(result);
        assertEquals(1, logged.size());
        assertEquals("stream-error:connection lost", logged.get(0));
    }

    private static Map<String, Object> buildChunk(String id, String model, String content, String finishReason, List<Map<String, Object>> toolCalls) {
        Map<String, Object> delta = new LinkedHashMap<>();
        if (content != null) {
            delta.put("content", content);
        }
        if (toolCalls != null) {
            delta.put("tool_calls", toolCalls);
        }

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        if (finishReason != null) {
            choice.put("finish_reason", finishReason);
        }

        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", id);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("model", model);
        chunk.put("choices", List.of(choice));
        return chunk;
    }
}
