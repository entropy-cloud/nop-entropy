package io.nop.rpc.core.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.PartialCommit;
import io.nop.api.core.message.TerminateConsume;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI6: D5(b-prime) transport signals (TerminateConsume / PartialCommit) returned by a
 * message-RPC handler must never leak into the RPC reply payload — they are
 * transport-level ack semantics, not business responses.
 */
public class TestRpcMessageSubscriberSignals {

    static class TestSubscriber extends RpcMessageSubscriber {
        TestSubscriber() {
            super("test-service", (topic, message, options) ->
                    java.util.concurrent.CompletableFuture.completedFuture(null));
        }

        @Override
        protected String getTopic(String serviceMethod, ApiRequest<?> request) {
            return serviceMethod;
        }
    }

    private ApiResponse<?> call(IMessageConsumer consumer) throws Exception {
        TestSubscriber subscriber = new TestSubscriber();
        subscriber.subscribe("t-signal", consumer, new MessageSubscribeOptions());
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData("x");
        CompletionStage<ApiResponse<?>> future = subscriber.callAsync("t-signal", request, null);
        return future.toCompletableFuture().get();
    }

    @Test
    public void terminateConsumeIsNotSerializedAsReplyPayload() throws Exception {
        ApiResponse<?> rep = call((topic, message, ctx) -> new TerminateConsume("poison"));
        assertNotNull(rep);
        assertNull(rep.getData(), "TerminateConsume must not become the RPC reply payload");
    }

    @Test
    public void partialCommitIsNotSerializedAsReplyPayload() throws Exception {
        ApiResponse<?> rep = call((topic, message, ctx) -> new PartialCommit(1));
        assertNotNull(rep);
        assertNull(rep.getData(), "PartialCommit must not become the RPC reply payload");
    }

    @Test
    public void normalReplyStillPassesThrough() throws Exception {
        ApiResponse<?> rep = call((topic, message, ctx) -> CompletableFuture
                .completedStage("business-reply"));
        assertEquals("business-reply", rep.getData());
    }
}
