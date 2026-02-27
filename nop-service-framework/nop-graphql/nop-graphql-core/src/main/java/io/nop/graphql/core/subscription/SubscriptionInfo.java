/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.subscription;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ws.IWebSocketSession;

import java.util.Map;
import java.util.concurrent.Flow;

/**
 * Holds information about an active GraphQL subscription.
 */
@DataBean
public class SubscriptionInfo {
    /**
     * Unique identifier for this subscription operation
     */
    private String operationId;

    /**
     * GraphQL operation name (e.g., "onUserChanged")
     */
    private String operationName;

    /**
     * Topic pattern this subscription is interested in
     * (e.g., "graphql-subscription/onUserChanged/123")
     */
    private String topic;

    /**
     * WebSocket session to push events to
     */
    private IWebSocketSession session;

    /**
     * GraphQL execution context for building responses
     */
    private IGraphQLExecutionContext executionContext;

    /**
     * Field selection for response transformation
     */
    private FieldSelectionBean fieldSelection;

    /**
     * Variables from the subscription request
     */
    private Map<String, Object> variables;

    /**
     * Flow subscription for backpressure handling
     */
    private Flow.Subscription flowSubscription;

    /**
     * Creation timestamp
     */
    private long createTime;

    public SubscriptionInfo() {
        this.createTime = System.currentTimeMillis();
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public IWebSocketSession getSession() {
        return session;
    }

    public void setSession(IWebSocketSession session) {
        this.session = session;
    }

    public IGraphQLExecutionContext getExecutionContext() {
        return executionContext;
    }

    public void setExecutionContext(IGraphQLExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public FieldSelectionBean getFieldSelection() {
        return fieldSelection;
    }

    public void setFieldSelection(FieldSelectionBean fieldSelection) {
        this.fieldSelection = fieldSelection;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public Flow.Subscription getFlowSubscription() {
        return flowSubscription;
    }

    public void setFlowSubscription(Flow.Subscription flowSubscription) {
        this.flowSubscription = flowSubscription;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    /**
     * Check if this subscription matches the given topic
     */
    public boolean matchesTopic(String messageTopic) {
        if (topic == null || messageTopic == null) {
            return false;
        }
        // Exact match or wildcard pattern match
        if (topic.equals(messageTopic)) {
            return true;
        }
        // Support wildcard: graphql-subscription/onUserChanged/* matches graphql-subscription/onUserChanged/123
        if (topic.endsWith("/*")) {
            String prefix = topic.substring(0, topic.length() - 1);
            return messageTopic.startsWith(prefix);
        }
        return false;
    }

    /**
     * Cancel the flow subscription if exists
     */
    public void cancel() {
        if (flowSubscription != null) {
            flowSubscription.cancel();
            flowSubscription = null;
        }
    }
}
