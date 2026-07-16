package io.nop.metadata.service.mock;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.TopicMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test mock for {@link IMessageService}. Records all {@code send}/{@code sendAsync} calls. Tests configure behavior
 * via {@code static} fields (the IoC bean is a singleton delegating to static state).
 *
 * <p>Usage in tests:
 * <ul>
 *   <li>{@link #reset()} — clear recorded sends + reset to default success (call in @BeforeEach)</li>
 *   <li>{@link #throwOnSend} — if non-null, {@code send} throws this instead of succeeding</li>
 *   <li>{@link #sendCallCount} — number of {@code send} calls since last reset</li>
 *   <li>{@link #lastTopic} / {@link #lastMessage} — the last topic/message passed to {@code send}</li>
 *   <li>{@link #recordedSends} — all (topic, message) pairs in order</li>
 * </ul>
 */
public class MockMessageService implements IMessageService {

    public static RuntimeException throwOnSend = null;
    public static int sendCallCount = 0;
    public static String lastTopic = null;
    public static Object lastMessage = null;
    public static final List<Object[]> recordedSends = new ArrayList<>();

    public static void reset() {
        throwOnSend = null;
        sendCallCount = 0;
        lastTopic = null;
        lastMessage = null;
        recordedSends.clear();
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        recordSend(topic, message);
        if (throwOnSend != null) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(throwOnSend);
            return f;
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void send(String topic, Object message, MessageSendOptions options) {
        recordSend(topic, message);
        if (throwOnSend != null) {
            throw throwOnSend;
        }
    }

    private static void recordSend(String topic, Object message) {
        sendCallCount++;
        lastTopic = topic;
        lastMessage = message;
        recordedSends.add(new Object[]{topic, message});
    }

    @Override
    public CompletionStage<Void> sendMultiAsync(Collection<TopicMessage> messages, MessageSendOptions options) {
        for (TopicMessage m : messages) {
            recordSend(m.getTopic(), m.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        return new IMessageSubscription() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isSuspended() {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return true;
            }

            @Override
            public void suspend() {
            }

            @Override
            public void resume() {
            }
        };
    }
}
