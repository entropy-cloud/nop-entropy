package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.client.jdk.JdkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Requires local LM Studio running at http://localhost:1234")
public class TestChatServiceImpl extends JunitBaseTestCase {

    private IChatService chatService;

    @BeforeEach
    void setUp() {
        ChatServiceImpl impl = new ChatServiceImpl();
        JdkHttpClient httpClient = new JdkHttpClient(new HttpClientConfig());
        httpClient.start();
        impl.setHttpClient(httpClient);
        impl.setChatLogger(new DefaultChatLogger());
        chatService = impl;
    }

    @Test
    void testCallAsync_stream() {
        ChatOptions options = ChatOptions.builder()
                .provider("lm-studio")
                .stream(true)
                .maxTokens(50)
                .build();

        ChatRequest request = ChatRequest.userPrompt("Say hello in one word.");
        request.setOptions(options);

        ChatResponse response = chatService.call(request, null);

        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getMessage(), "Message should not be null");
        assertNotNull(response.getMessage().getContent(), "Content should not be null");
        assertFalse(response.getMessage().getContent().isBlank(), "Content should not be blank");
        assertNotNull(response.getRequestId(), "RequestId should not be null");
        assertTrue(response.getResponseTime() > 0, "ResponseTime should be positive");

        System.out.println("Content: " + response.getMessage().getContent());
        if (response.getUsage() != null) {
            System.out.println("Usage: promptTokens=" + response.getUsage().getPromptTokens()
                    + ", completionTokens=" + response.getUsage().getCompletionTokens());
        }
    }

    @Test
    void testCallAsync_nonStream() {
        ChatOptions options = ChatOptions.builder()
                .provider("lm-studio")
                .stream(false)
                .maxTokens(50)
                .build();

        ChatRequest request = ChatRequest.userPrompt("Say hello in one word.");
        request.setOptions(options);

        ChatResponse response = chatService.call(request, null);

        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getMessage(), "Message should not be null");
        assertNotNull(response.getMessage().getContent(), "Content should not be null");
        assertFalse(response.getMessage().getContent().isBlank(), "Content should not be blank");

        System.out.println("Content: " + response.getMessage().getContent());
        if (response.getUsage() != null) {
            System.out.println("Usage: promptTokens=" + response.getUsage().getPromptTokens()
                    + ", completionTokens=" + response.getUsage().getCompletionTokens());
        }
    }

    @Test
    void testCallStream() throws Exception {
        ChatOptions options = ChatOptions.builder()
                .provider("lm-studio")
                .maxTokens(100)
                .build();

        ChatRequest request = ChatRequest.userPrompt("Count from 1 to 5.");
        request.setOptions(options);

        StringBuilder fullContent = new StringBuilder();
        boolean[] completed = {false};
        ChatUsage[] lastUsage = {null};

        chatService.callStream(request, null).subscribe(new Flow.Subscriber<>() {
            Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatStreamChunk chunk) {
                if (chunk.hasContent()) {
                    fullContent.append(chunk.getContent());
                    System.out.print(chunk.getContent());
                }
                if (chunk.getUsage() != null) {
                    lastUsage[0] = chunk.getUsage();
                }
                if (chunk.isLastChunk()) {
                    System.out.println("\n[finishReason=" + chunk.getFinishReason() + "]");
                }
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
                completed[0] = true;
            }

            @Override
            public void onComplete() {
                completed[0] = true;
            }
        });

        TimeUnit.SECONDS.sleep(30);
        assertTrue(completed[0], "Stream should complete");
        assertFalse(fullContent.toString().isBlank(), "Should have received content chunks");

        if (lastUsage[0] != null) {
            System.out.println("Stream usage: promptTokens=" + lastUsage[0].getPromptTokens()
                    + ", completionTokens=" + lastUsage[0].getCompletionTokens());
        }
    }
}
