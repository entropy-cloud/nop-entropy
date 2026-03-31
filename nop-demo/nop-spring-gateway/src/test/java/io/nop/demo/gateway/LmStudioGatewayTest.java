package io.nop.demo.gateway;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.FutureHelper;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(classes = SpringGatewayDemoMain.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled
class LmStudioGatewayTest {

    @LocalServerPort
    int port;

    IHttpClient httpClient;

    static final String LM_STUDIO_MODEL = "qwen/qwen3-8b";

    @BeforeEach
    void setUp() {
        httpClient = BeanContainer.getBeanByType(IHttpClient.class);
    }

    private String gatewayUrl(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void testListModels() {
        HttpRequest request = HttpRequest.get(gatewayUrl("/v1/models"))
                .header("Content-Type", "application/json");

        IHttpResponse response = FutureHelper.syncGet(httpClient.fetchAsync(request, null));

        assertEquals(200, response.getHttpStatus(), "Body: " + response.getBodyAsString());

        Map<String, Object> body = (Map<String, Object>) JSON.parse(response.getBodyAsString());
        assertNotNull(body);
        System.out.println(body);

        if (body.containsKey("data")) {
            assertTrue(body.get("data") instanceof List, "models response should contain data array");
        }
    }

    @Test
    void testChatCompletionNonStreaming() {
        Map<String, Object> chatRequest = Map.of(
                "model", LM_STUDIO_MODEL,
                "messages", List.of(
                        Map.of("role", "user", "content", "Say hi in one word.")
                ),
                "max_tokens", 20,
                "temperature", 0.1,
                "stream", false
        );

        HttpRequest request = HttpRequest.post(gatewayUrl("/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .body(JSON.stringify(chatRequest))
                .timeout(120000);

        IHttpResponse response = FutureHelper.syncGet(httpClient.fetchAsync(request, null));

        assertEquals(200, response.getHttpStatus(), "Body: " + response.getBodyAsString());

        Map<String, Object> body = (Map<String, Object>) JSON.parse(response.getBodyAsString());
        System.out.println(body);

        assertTrue(body.containsKey("choices"),
                "Response should contain 'choices'. Body: " + response.getBodyAsString());

        List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
        assertFalse(choices.isEmpty(), "Should have at least one choice");
        assertTrue(choices.get(0).containsKey("message"), "Choice should have 'message'");
    }

    @Test
    void testChatCompletionStreaming() throws InterruptedException {
        Map<String, Object> chatRequest = Map.of(
                "model", LM_STUDIO_MODEL,
                "messages", List.of(
                        Map.of("role", "user", "content", "Say hi")
                ),
                "max_tokens", 20,
                "temperature", 0.1,
                "stream", true
        );

        HttpRequest request = HttpRequest.post(gatewayUrl("/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .body(JSON.stringify(chatRequest))
                .timeout(120000);

        Flow.Publisher<IServerEventResponse> eventFlow = httpClient.fetchServerEventFlow(request, null);

        List<Map<String, Object>> chunks = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        eventFlow.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(IServerEventResponse item) {
                String data = item.getData();
                if (data != null) {
                    try {
                        Map<String, Object> chunk = (Map<String, Object>) JSON.parse(data);
                        chunks.add(chunk);
                    } catch (Exception e) {
                        errors.add(e);
                    }
                }
                subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                errors.add(throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        if (!latch.await(120, TimeUnit.SECONDS)) {
            fail("Streaming timed out after 120 seconds");
        }

        System.out.println(chunks);

        assertTrue(errors.isEmpty(), "No errors should occur. Errors: " + errors);
        assertFalse(chunks.isEmpty(), "Should receive at least one SSE chunk");

        Map<String, Object> firstChunk = chunks.get(0);
        assertTrue(firstChunk.containsKey("choices"),
                "First chunk should contain 'choices': " + firstChunk);
    }
}