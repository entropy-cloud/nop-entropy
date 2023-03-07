/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.graphql.core.ws;

import io.nop.api.core.beans.graphql.GraphQLErrorBean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.json.JSON;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.DestroyHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public abstract class AbstractGraphQLWebsocketHandler implements IWebSocketHandler {
    protected final Logger LOG = LoggerFactory.getLogger(AbstractGraphQLWebsocketHandler.class);

    protected final Function<GraphQLRequestBean, Flow.Publisher<GraphQLResponseBean>> executionService;
    protected final IWebSocketSession session;
    protected final AtomicBoolean connectionInitialized;
    protected final String connectionAckMessage;
    protected final Map<String, Flow.Subscriber<GraphQLResponseBean>> activeOperations;
    private int maxActiveOperations = 1000;
    protected final Future<?> keepAliveSender;
    private final String dataMessageTypeName;

    public AbstractGraphQLWebsocketHandler(
            Function<GraphQLRequestBean, Flow.Publisher<GraphQLResponseBean>> executionService,
            IWebSocketSession session, String dataMessageTypeName) {
        this.executionService = executionService;
        this.session = session;
        this.dataMessageTypeName = dataMessageTypeName;
        this.connectionInitialized = new AtomicBoolean(false);
        this.connectionAckMessage = createConnectionAckMessage();
        this.activeOperations = new ConcurrentHashMap<>();
        this.keepAliveSender = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::sendKeepAlive, 10, 10,
                TimeUnit.SECONDS);
    }

    public void setMaxActiveOperations(int maxActiveOperations) {
        this.maxActiveOperations = maxActiveOperations;
    }

    @Override
    public void onMessage(String text) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("<<< " + text);
        }
        onMessage(getMessageAsJsonObject(text));
    }

    @Override
    public void onThrowable(Throwable t) {
        LOG.warn("Error in websocket", t);
        if (keepAliveSender != null) {
            keepAliveSender.cancel(false);
        }
    }

    @Override
    public void onClose() {
        LOG.debug("nop.websocket.session-closed:{}", session);
        activeOperations.forEach((id, operation) -> cancelOperation(id));
        if (!session.isClosed()) {
            session.close((short) 1000, "");
        }
        if (keepAliveSender != null) {
            keepAliveSender.cancel(false);
        }
    }

    @Override
    public void onEnd() {
    }

    protected void sendConnectionAckMessage() throws IOException {
        if (connectionInitialized.getAndSet(true)) {
            session.close((short) 4429, "Too many initialisation requests");
        } else {
            session.sendMessage(connectionAckMessage);
        }
    }

    protected void sendDataMessage(Map<String, Object> message) throws IOException {
        String operationId = (String) message.get("id");
        if (validSubscription(operationId)) {
            Map<String, Object> payload = (Map<String, Object>) message.get("payload");
            GraphQLRequestBean request = BeanTool.castBeanToType(payload, GraphQLRequestBean.class);

            Flow.Publisher<GraphQLResponseBean> stream = executionService.apply(request);
            sendStreamingMessage(operationId, stream);
        }
    }

    private String createConnectionAckMessage() {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("type", "connection_ack");
        return JSON.stringify(ret);
    }

    private Map<String, Object> getMessageAsJsonObject(String text) {
        if (StringHelper.isEmpty(text))
            return null;

        try {
            return (Map<String, Object>) JSON.parse(text);
        } catch (Exception ex) {
            session.close((short) 4400, ex.getMessage());
            return null;
        }
    }

    private String createCompleteMessage(String operationId) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("type", "complete");
        ret.put("id", operationId);
        return JSON.stringify(ret);
    }

    private String createDataMessage(String operationId, Object payload) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("type", this.dataMessageTypeName);
        ret.put("id", operationId);
        ret.put("payload", payload);
        return JSON.stringify(ret);
    }

    void sendSingleMessage(String operationId, GraphQLResponseBean executionResponse) throws IOException {
        session.sendMessage(createDataMessage(operationId, executionResponse));
        session.sendMessage(createCompleteMessage(operationId));
    }

    private void sendStreamingMessage(String operationId, Flow.Publisher<GraphQLResponseBean> stream) {
        SubscriptionSubscriber subscriber = new SubscriptionSubscriber(session, operationId);
        Flow.Subscriber<GraphQLResponseBean> old = activeOperations.put(operationId, subscriber);
        if (old != null && old != stream) {
            DestroyHelper.safeDestroy(old);
        }
        stream.subscribe(subscriber);
    }

    private void sendKeepAlive() {
        try {
            session.sendMessage(getPingMessage());
        } catch (IOException e) {
            LOG.warn("nop.websocket.send-keep-alive-fail", e);
        }
    }

    protected void sendCancelMessage(Map<String, Object> message) {
        String opId = (String) message.get("id");
        boolean cancelled = cancelOperation(opId);
        if (cancelled) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("nop.websocket.cancel-operation:{}", opId);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("nop.websocket.cancel-operation-not-active:{}", opId);
            }
        }
    }

    // cancel the operation with this id, returns true if it actually cancels an operation,
    // false if no such operation is active
    private boolean cancelOperation(String opId) {
        Flow.Subscriber<?> subscriber = activeOperations.remove(opId);
        if (subscriber != null) {
            if (subscriber instanceof SubscriptionSubscriber) {
                ((SubscriptionSubscriber) subscriber).cancel();
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean validSubscription(String operationId) throws IOException {
        if (!connectionInitialized.get()) {
            closeDueToConnectionNotInitialized();
            return false;
        }

        if (activeOperations.size() >= maxActiveOperations) {
            LOG.warn("nop.websocket.too-many-active-operations:{}", operationId);
            GraphQLResponseBean response = new GraphQLResponseBean();
            GraphQLErrorBean error = new GraphQLErrorBean();
            error.setMessage("too many active operations");
            response.setErrors(Arrays.asList(error));
            sendErrorMessage(operationId, response);
            return false;
        }

        if (activeOperations.containsKey(operationId)) {
            session.close((short) 4409, "Subscriber for " + operationId + " already exists");
            return false;
        }
        return true;
    }

    protected abstract void onMessage(Map<String, Object> message);

    protected abstract void sendErrorMessage(String operationId, GraphQLResponseBean executionResponse)
            throws IOException;

    protected abstract void closeDueToConnectionNotInitialized();

    protected abstract String getPingMessage();

    /**
     * The middleman that subscribes to an execution result and forwards its events to the websocket channel.
     */
    private class SubscriptionSubscriber implements Flow.Subscriber<GraphQLResponseBean> {

        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        private final IWebSocketSession session;
        private final String operationId;

        public SubscriptionSubscriber(IWebSocketSession session, String operationId) {
            this.session = session;
            this.operationId = operationId;
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            subscription.set(s);
            subscription.get().request(1);
        }

        @Override
        public void onNext(GraphQLResponseBean executionResult) {
            if (!session.isClosed()) {
                try {
                    if (executionResult.hasError()) {
                        sendErrorMessage(operationId, executionResult);
                    } else {
                        session.sendMessage(createDataMessage(operationId, executionResult));
                    }
                } catch (Exception e) {
                    LOG.warn("nop.websocket.send-next-fail", e);
                }
                subscription.get().request(1);
            }
        }

        @Override
        public void onError(Throwable t) {
            // TODO: I'm not sure if/when this can happen. Even if the operation's root fails, it goes into `onNext`.
            LOG.error("nop.websocket.error", t);
        }

        @Override
        public void onComplete() {
            if (LOG.isTraceEnabled()) {
                LOG.trace("nop.websocket.subscription-completed:opId={}", operationId);
            }

            try {
                session.sendMessage(createCompleteMessage(operationId));
            } catch (Exception e) {
                LOG.warn("nop.websocket.send-fail", e);
            }
            activeOperations.remove(operationId);
        }

        public void cancel() {
            Flow.Subscription sub = subscription.get();
            if (sub != null) {
                sub.cancel();
            }
        }
    }
}