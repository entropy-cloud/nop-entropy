/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.subscription;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.GraphQLExecutionContext;
import io.nop.graphql.core.engine.MockGraphQLSchemaLoader;
import io.nop.graphql.core.engine.GraphQLEngine;
import io.nop.graphql.core.ws.IWebSocketSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_SUBSCRIPTION_DUPLICATE_OPERATION;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_SUBSCRIPTION_TOO_MANY;
import static org.junit.jupiter.api.Assertions.*;

public class TestGraphQLSubscriptionManager {

    private GraphQLSubscriptionManager manager;
    private TestableMessageService testMessageService;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        testMessageService = new TestableMessageService();

        manager = new GraphQLSubscriptionManager();
        manager.setMessageService(testMessageService);
        manager.setMaxActiveSubscriptions(10);
    }

    @AfterEach
    public void tearDown() {
        if (manager != null) {
            manager.destroy();
        }
    }

    @Test
    public void testStart_subscribesToMessageService() {
        manager.start();

        assertTrue(testMessageService.subscribed);
        assertEquals("graphql-subscription/*", testMessageService.subscribedTopic);
    }

    @Test
    public void testStart_withoutMessageService() {
        manager.setMessageService(null);
        manager.start();

        assertFalse(testMessageService.subscribed);
    }

    @Test
    public void testRegisterSubscription() {
        manager.start();

        SubscriptionInfo subscription = createTestSubscription("op-1", "onUserChanged");
        manager.registerSubscription(subscription);

        assertEquals(1, manager.getActiveSubscriptionCount());
        assertEquals(subscription, manager.getSubscription("op-1"));
    }

    @Test
    public void testRegisterSubscription_duplicateOperation_throws() {
        manager.start();

        SubscriptionInfo sub1 = createTestSubscription("op-1", "onUserChanged");
        manager.registerSubscription(sub1);

        SubscriptionInfo sub2 = createTestSubscription("op-1", "onOrderChanged");
        
        NopException ex = assertThrows(NopException.class, () -> {
            manager.registerSubscription(sub2);
        });
        
        assertEquals(ERR_GRAPHQL_SUBSCRIPTION_DUPLICATE_OPERATION.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testRegisterSubscription_tooManySubscriptions_throws() {
        manager.setMaxActiveSubscriptions(2);
        manager.start();

        manager.registerSubscription(createTestSubscription("op-1", "onUserChanged"));
        manager.registerSubscription(createTestSubscription("op-2", "onOrderChanged"));

        NopException ex = assertThrows(NopException.class, () -> {
            manager.registerSubscription(createTestSubscription("op-3", "onProductChanged"));
        });
        
        assertEquals(ERR_GRAPHQL_SUBSCRIPTION_TOO_MANY.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testUnregisterSubscription() {
        manager.start();

        SubscriptionInfo subscription = createTestSubscription("op-1", "onUserChanged");
        manager.registerSubscription(subscription);

        assertEquals(1, manager.getActiveSubscriptionCount());

        manager.unregisterSubscription("op-1");

        assertEquals(0, manager.getActiveSubscriptionCount());
        assertNull(manager.getSubscription("op-1"));
    }

    @Test
    public void testUnregisterSubscription_nonExistent() {
        manager.start();

        manager.unregisterSubscription("non-existent");
        assertEquals(0, manager.getActiveSubscriptionCount());
    }

    @Test
    public void testGetSubscriptionsByOperation() {
        manager.start();

        SubscriptionInfo sub1 = createTestSubscription("op-1", "onUserChanged");
        SubscriptionInfo sub2 = createTestSubscription("op-2", "onUserChanged");
        SubscriptionInfo sub3 = createTestSubscription("op-3", "onOrderChanged");

        manager.registerSubscription(sub1);
        manager.registerSubscription(sub2);
        manager.registerSubscription(sub3);

        List<SubscriptionInfo> userSubs = manager.getSubscriptionsByOperation("onUserChanged");
        assertEquals(2, userSubs.size());
        assertTrue(userSubs.contains(sub1));
        assertTrue(userSubs.contains(sub2));

        List<SubscriptionInfo> orderSubs = manager.getSubscriptionsByOperation("onOrderChanged");
        assertEquals(1, orderSubs.size());
        assertTrue(orderSubs.contains(sub3));

        List<SubscriptionInfo> nonExistent = manager.getSubscriptionsByOperation("nonExistent");
        assertTrue(nonExistent.isEmpty());
    }

    @Test
    public void testCancelSessionSubscriptions() {
        manager.start();

        MockWebSocketSession session1 = new MockWebSocketSession();
        MockWebSocketSession session2 = new MockWebSocketSession();

        SubscriptionInfo sub1 = createTestSubscription("op-1", "onUserChanged", session1);
        SubscriptionInfo sub2 = createTestSubscription("op-2", "onOrderChanged", session1);
        SubscriptionInfo sub3 = createTestSubscription("op-3", "onProductChanged", session2);

        manager.registerSubscription(sub1);
        manager.registerSubscription(sub2);
        manager.registerSubscription(sub3);

        assertEquals(3, manager.getActiveSubscriptionCount());

        manager.cancelSessionSubscriptions(session1);

        assertEquals(1, manager.getActiveSubscriptionCount());
        assertNull(manager.getSubscription("op-1"));
        assertNull(manager.getSubscription("op-2"));
        assertEquals(sub3, manager.getSubscription("op-3"));
    }

    @Test
    public void testBuildTopic() {
        assertEquals("graphql-subscription/onUserChanged/123",
                GraphQLSubscriptionManager.buildTopic("onUserChanged", "123"));
        assertEquals("graphql-subscription/onOrderChanged/order-456",
                GraphQLSubscriptionManager.buildTopic("onOrderChanged", "order-456"));
    }

    @Test
    public void testBuildTopicPattern() {
        assertEquals("graphql-subscription/onUserChanged/*",
                GraphQLSubscriptionManager.buildTopicPattern("onUserChanged"));
    }

    @Test
    public void testParseOperationName() {
        assertEquals("onUserChanged",
                GraphQLSubscriptionManager.parseOperationName("graphql-subscription/onUserChanged/123"));
        assertEquals("onOrderChanged",
                GraphQLSubscriptionManager.parseOperationName("graphql-subscription/onOrderChanged/abc"));
        assertNull(GraphQLSubscriptionManager.parseOperationName(null));
        assertNull(GraphQLSubscriptionManager.parseOperationName(""));
        assertNull(GraphQLSubscriptionManager.parseOperationName("invalid-topic"));
    }

    @Test
    public void testParseEntityId() {
        assertEquals("123",
                GraphQLSubscriptionManager.parseEntityId("graphql-subscription/onUserChanged/123"));
        assertEquals("abc-def",
                GraphQLSubscriptionManager.parseEntityId("graphql-subscription/onOrderChanged/abc-def"));
        assertNull(GraphQLSubscriptionManager.parseEntityId("graphql-subscription/onUserChanged"));
        assertNull(GraphQLSubscriptionManager.parseEntityId("graphql-subscription/onUserChanged/"));
        assertNull(GraphQLSubscriptionManager.parseEntityId(null));
    }

    @Test
    public void testDestroy() {
        manager.start();

        manager.registerSubscription(createTestSubscription("op-1", "onUserChanged"));
        manager.registerSubscription(createTestSubscription("op-2", "onOrderChanged"));

        assertEquals(2, manager.getActiveSubscriptionCount());

        manager.destroy();

        assertEquals(0, manager.getActiveSubscriptionCount());
        assertTrue(testMessageService.subscriptionCancelled);
    }

    @Test
    public void testOnMessage_withMatchingSubscription() throws Exception {
        manager.start();

        MockWebSocketSession session = new MockWebSocketSession();
        SubscriptionInfo subscription = createTestSubscription("op-1", "onUserChanged", session);
        subscription.setTopic("graphql-subscription/onUserChanged/123");
        manager.registerSubscription(subscription);

        Map<String, Object> message = Map.of("userId", "123", "action", "updated");
        testMessageService.deliverMessage("graphql-subscription/onUserChanged/123", message);

        List<String> sentMessages = session.getSentMessages();
        assertFalse(sentMessages.isEmpty());
        assertTrue(sentMessages.get(0).contains("\"jsonrpc\":\"2.0\""));
        assertTrue(sentMessages.get(0).contains("\"id\":\"op-1\""));
        assertTrue(sentMessages.get(0).contains("\"result\""));
    }

    @Test
    public void testOnMessage_withNonMatchingSubscription() throws Exception {
        manager.start();

        MockWebSocketSession session = new MockWebSocketSession();
        SubscriptionInfo subscription = createTestSubscription("op-1", "onUserChanged", session);
        subscription.setTopic("graphql-subscription/onUserChanged/456");
        manager.registerSubscription(subscription);

        Map<String, Object> message = Map.of("userId", "123", "action", "updated");
        testMessageService.deliverMessage("graphql-subscription/onUserChanged/123", message);

        List<String> sentMessages = session.getSentMessages();
        assertTrue(sentMessages.isEmpty());
    }

    @Test
    public void testOnMessage_withClosedSession() throws Exception {
        manager.start();

        MockWebSocketSession session = new MockWebSocketSession();
        session.close((short) 1000, "test");

        SubscriptionInfo subscription = createTestSubscription("op-1", "onUserChanged", session);
        subscription.setTopic("graphql-subscription/onUserChanged/123");
        manager.registerSubscription(subscription);

        Map<String, Object> message = Map.of("userId", "123");
        testMessageService.deliverMessage("graphql-subscription/onUserChanged/123", message);

        assertNull(manager.getSubscription("op-1"));
    }

    @Test
    public void testOnMessage_withWildcardTopic() throws Exception {
        manager.start();

        MockWebSocketSession session = new MockWebSocketSession();
        SubscriptionInfo subscription = createTestSubscription("op-1", "onUserChanged", session);
        subscription.setTopic("graphql-subscription/onUserChanged/*");
        manager.registerSubscription(subscription);

        Map<String, Object> message = Map.of("userId", "123", "action", "updated");
        testMessageService.deliverMessage("graphql-subscription/onUserChanged/123", message);

        List<String> sentMessages = session.getSentMessages();
        assertFalse(sentMessages.isEmpty());
    }

    @Test
    public void testGetAllSubscriptions() {
        manager.start();

        SubscriptionInfo sub1 = createTestSubscription("op-1", "onUserChanged");
        SubscriptionInfo sub2 = createTestSubscription("op-2", "onOrderChanged");

        manager.registerSubscription(sub1);
        manager.registerSubscription(sub2);

        assertEquals(2, manager.getAllSubscriptions().size());
        assertTrue(manager.getAllSubscriptions().contains(sub1));
        assertTrue(manager.getAllSubscriptions().contains(sub2));
    }

    private SubscriptionInfo createTestSubscription(String operationId, String operationName) {
        return createTestSubscription(operationId, operationName, new MockWebSocketSession());
    }

    private SubscriptionInfo createTestSubscription(String operationId, String operationName, IWebSocketSession session) {
        SubscriptionInfo info = new SubscriptionInfo();
        info.setOperationId(operationId);
        info.setOperationName(operationName);
        info.setSession(session);
        info.setTopic(GraphQLSubscriptionManager.buildTopic(operationName, "test-id"));
        
        IGraphQLExecutionContext context = new GraphQLExecutionContext(new ServiceContextImpl());
        info.setExecutionContext(context);
        info.setFieldSelection(FieldSelectionBean.fromProp("data"));
        
        return info;
    }

    private static class TestableMessageService implements IMessageService {
        boolean subscribed = false;
        boolean subscriptionCancelled = false;
        String subscribedTopic;
        private io.nop.api.core.message.IMessageConsumer messageConsumer;

        private final IMessageSubscription subscription = new IMessageSubscription() {
            @Override
            public void cancel() {
                subscriptionCancelled = true;
            }

            @Override
            public boolean isSuspended() {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return subscriptionCancelled;
            }

            @Override
            public void suspend() {
            }

            @Override
            public void resume() {
            }
        };

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, io.nop.api.core.message.MessageSendOptions options) {
            return null;
        }

        @Override
        public IMessageSubscription subscribe(String topic, io.nop.api.core.message.IMessageConsumer listener, MessageSubscribeOptions options) {
            this.subscribed = true;
            this.subscribedTopic = topic;
            this.messageConsumer = listener;
            return subscription;
        }

        public void deliverMessage(String topic, Object message) {
            if (messageConsumer != null) {
                messageConsumer.onMessage(topic, message, null);
            }
        }
    }

    private static class MockWebSocketSession implements IWebSocketSession {
        private final List<String> sentMessages = new ArrayList<>();
        private boolean closed = false;

        @Override
        public void sendMessage(String message) throws IOException {
            if (closed) {
                throw new IOException("Session is closed");
            }
            sentMessages.add(message);
        }

        @Override
        public void close(short statusCode, String reason) {
            this.closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        public List<String> getSentMessages() {
            return sentMessages;
        }
    }
}
