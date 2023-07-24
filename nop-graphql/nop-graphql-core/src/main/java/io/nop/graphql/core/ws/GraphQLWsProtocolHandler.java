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

import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.json.JSON;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.function.Function;

/**
 * Websocket subprotocol handler that implements the `graphql-transport-ws` subprotocol.
 */
public class GraphQLWsProtocolHandler extends AbstractGraphQLWebsocketHandler {

    private final String pingMessage;
    private final String pongMessage;

    public GraphQLWsProtocolHandler(Function<GraphQLRequestBean, Flow.Publisher<GraphQLResponseBean>> executionService,
                                    IWebSocketSession session) {
        super(executionService, session, "next");
        this.pingMessage = createPingMessage();
        this.pongMessage = createPongMessage();
    }

    @Override
    protected void onMessage(Map<String, Object> message) {
        if (message != null) {
            MessageType messageType = getMessageType(message);
            try {
                switch (messageType) {
                    case CONNECTION_INIT:
                        sendConnectionAckMessage();
                        break;
                    case PING:
                        sendPongMessage();
                        break;
                    case PONG:
                        break;
                    case SUBSCRIBE:
                        sendDataMessage(message);
                        break;
                    case COMPLETE:
                        sendCancelMessage(message);
                        break;
                    case CONNECTION_ACK:
                    case NEXT:
                    case ERROR:
                        LOG.debug("nop.websocket.ignore-error", message);
                        break;
                }
            } catch (IOException e) {
                LOG.warn("nop.websocket.on-message-error", e);
            }
        }
    }

    private MessageType getMessageType(Map<String, Object> message) {
        return MessageType.fromString((String) message.get("type"));
    }

    @Override
    protected void closeDueToConnectionNotInitialized() {
        session.close((short) 4401, "Unauthorized");
    }

    @Override
    protected void sendErrorMessage(String operationId, GraphQLResponseBean executionResponse) throws IOException {
        session.sendMessage(createErrorMessage(operationId, executionResponse.getErrors()));
    }

    private String createErrorMessage(String operationId, List<?> errors) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("id", operationId);
        ret.put("type", "error");
        ret.put("payload", errors);
        return JSON.stringify(ret);
    }

    private void sendPongMessage() throws IOException {
        session.sendMessage(pongMessage);
    }

    @Override
    protected String getPingMessage() {
        return pingMessage;
    }

    private String createPongMessage() {
        return "{\"type\":\"pong\"}";
    }

    private String createPingMessage() {
        return "{\"type\":\"ping\"}";
    }

}
