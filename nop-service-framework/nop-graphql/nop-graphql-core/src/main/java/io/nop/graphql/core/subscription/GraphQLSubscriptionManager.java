/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.subscription;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.graphql.core.ws.IWebSocketSession;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static io.nop.graphql.core.GraphQLErrors.*;

/**
 * Manages active GraphQL subscriptions and routes messages from IMessageService to WebSocket clients.
 *
 * <p>This manager bridges the message queue (IMessageService) with WebSocket-based GraphQL subscriptions.
 * When a data change event is published to a topic like "graphql-subscription/onUserChanged/123",
 * the manager routes it to all WebSocket clients that have subscribed to matching operations.</p>
 */
public class GraphQLSubscriptionManager {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLSubscriptionManager.class);

    private static final String TOPIC_PREFIX = "graphql-subscription/";

    private IMessageService messageService;
    private int maxActiveSubscriptions = 1000;

    private final Map<String, SubscriptionInfo> subscriptionsById = new ConcurrentHashMap<>();
    private final Map<String, List<SubscriptionInfo>> subscriptionsByOperation = new ConcurrentHashMap<>();
    private IMessageSubscription messageSubscription;

    public void setMessageService(IMessageService messageService) {
        this.messageService = messageService;
    }

    public void setMaxActiveSubscriptions(int maxActiveSubscriptions) {
        this.maxActiveSubscriptions = maxActiveSubscriptions;
    }

    /**
     * Subscribe to message service topics and start routing events
     */
    public void start() {
        if (messageService != null) {
            MessageSubscribeOptions options = new MessageSubscribeOptions();
            options.setSubscribeName("graphql-subscription-manager");

            messageSubscription = messageService.subscribe(
                    TOPIC_PREFIX + "*",
                    this::onMessage,
                    options
            );
            LOG.info("nop.graphql.subscription-manager-started:topic={}", TOPIC_PREFIX + "*");
        }
    }

    /**
     * Register a new WebSocket subscription
     */
    public void registerSubscription(SubscriptionInfo subscription) {
        String operationId = subscription.getOperationId();

        if (subscriptionsById.size() >= maxActiveSubscriptions) {
            throw new NopException(ERR_GRAPHQL_SUBSCRIPTION_TOO_MANY)
                    .param(ARG_OPERATION_ID, operationId);
        }

        if (subscriptionsById.containsKey(operationId)) {
            throw new NopException(ERR_GRAPHQL_SUBSCRIPTION_DUPLICATE_OPERATION)
                    .param(ARG_OPERATION_ID, operationId);
        }

        subscriptionsById.put(operationId, subscription);
        subscriptionsByOperation
                .computeIfAbsent(subscription.getOperationName(), k -> new CopyOnWriteArrayList<>())
                .add(subscription);

        LOG.debug("nop.graphql.subscription-registered:opId={},opName={},topic={}",
                operationId, subscription.getOperationName(), subscription.getTopic());
    }

    /**
     * Unregister a WebSocket subscription
     */
    public void unregisterSubscription(String operationId) {
        SubscriptionInfo subscription = subscriptionsById.remove(operationId);
        if (subscription != null) {
            subscription.cancel();

            List<SubscriptionInfo> ops = subscriptionsByOperation.get(subscription.getOperationName());
            if (ops != null) {
                ops.remove(subscription);
                if (ops.isEmpty()) {
                    subscriptionsByOperation.remove(subscription.getOperationName());
                }
            }

            LOG.debug("nop.graphql.subscription-unregistered:opId={}", operationId);
        }
    }

    /**
     * Cancel all subscriptions for a WebSocket session
     */
    public void cancelSessionSubscriptions(IWebSocketSession session) {
        List<String> toRemove = subscriptionsById.values().stream()
                .filter(s -> s.getSession() == session)
                .map(SubscriptionInfo::getOperationId)
                .collect(Collectors.toList());

        toRemove.forEach(this::unregisterSubscription);
    }

    /**
     * Get active subscription by operation ID
     */
    public SubscriptionInfo getSubscription(String operationId) {
        return subscriptionsById.get(operationId);
    }

    /**
     * Get all subscriptions for an operation name
     */
    public List<SubscriptionInfo> getSubscriptionsByOperation(String operationName) {
        return subscriptionsByOperation.getOrDefault(operationName, List.of());
    }

    /**
     * Get all active subscriptions
     */
    public Collection<SubscriptionInfo> getAllSubscriptions() {
        return subscriptionsById.values();
    }

    /**
     * Get count of active subscriptions
     */
    public int getActiveSubscriptionCount() {
        return subscriptionsById.size();
    }

    /**
     * Handle incoming message from IMessageService
     */
    protected Object onMessage(String topic, Object message, IMessageConsumeContext context) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("nop.graphql.subscription-message-received:topic={}", topic);
        }

        List<SubscriptionInfo> matchedSubscriptions = findMatchingSubscriptions(topic);

        String text = JsonTool.stringify(message);

        for (SubscriptionInfo subscription : matchedSubscriptions) {
            try {
                pushToClient(subscription, topic, text);
            } catch (Exception e) {
                LOG.warn("nop.graphql.subscription-push-fail:opId={},topic={}",
                        subscription.getOperationId(), topic, e);
            }
        }

        return null;
    }

    /**
     * Find subscriptions matching the given topic
     */
    protected List<SubscriptionInfo> findMatchingSubscriptions(String topic) {
        return subscriptionsById.values().stream()
                .filter(s -> s.matchesTopic(topic))
                .collect(Collectors.toList());
    }

    /**
     * Push message to WebSocket client
     */
    protected void pushToClient(SubscriptionInfo subscription, String topic, String message) throws IOException {
        IWebSocketSession session = subscription.getSession();
        if (session == null || session.isClosed()) {
            unregisterSubscription(subscription.getOperationId());
            return;
        }

        session.sendMessage(message);
    }

    /**
     * Build topic name for publishing subscription events
     */
    public static String buildTopic(String operationName, String id) {
        return TOPIC_PREFIX + operationName + "/" + id;
    }

    /**
     * Build topic pattern for subscribing to all events for an operation
     */
    public static String buildTopicPattern(String operationName) {
        return TOPIC_PREFIX + operationName + "/*";
    }

    /**
     * Parse operation name from topic
     */
    public static String parseOperationName(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        String suffix = topic.substring(TOPIC_PREFIX.length());
        int slashIndex = suffix.indexOf('/');
        if (slashIndex > 0) {
            return suffix.substring(0, slashIndex);
        }
        return suffix;
    }

    /**
     * Parse entity ID from topic
     */
    public static String parseEntityId(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        String suffix = topic.substring(TOPIC_PREFIX.length());
        int slashIndex = suffix.indexOf('/');
        if (slashIndex > 0 && slashIndex < suffix.length() - 1) {
            return suffix.substring(slashIndex + 1);
        }
        return null;
    }

    @PreDestroy
    public void destroy() {
        if (messageSubscription != null) {
            try {
                messageSubscription.cancel();
            } catch (Exception e) {
                LOG.error("nop.graphql.subscription-manager-cancel-fail", e);
            }
            messageSubscription = null;
        }

        subscriptionsById.values().forEach(SubscriptionInfo::cancel);
        subscriptionsById.clear();
        subscriptionsByOperation.clear();

        LOG.info("nop.graphql.subscription-manager-destroyed");
    }
}
