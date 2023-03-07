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

// copy from smallrye-graphql

import java.io.IOException;

/**
 * This is a simple abstraction over a websocket session to be able to abstract away from the underlying API. The reason
 * is to be able to implement protocol handlers which will work with Vert.x websockets as well as JSR-356 endpoints.
 */
public interface IWebSocketSession {

    void sendMessage(String message) throws IOException;

    void close(short statusCode, String reason);

    boolean isClosed();
}
