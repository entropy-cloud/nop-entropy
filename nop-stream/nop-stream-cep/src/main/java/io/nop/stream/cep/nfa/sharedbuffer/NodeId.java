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

package io.nop.stream.cep.nfa.sharedbuffer;

import java.util.Objects;

/**
 * Unique identifier for {@link SharedBufferNode}.
 */
public class NodeId {

    private final String pageName;
    private final EventId eventId;

    public NodeId(EventId eventId, String pageName) {
        this.eventId = eventId;
        this.pageName = pageName;
    }

    public EventId getEventId() {
        return eventId;
    }

    public String getPageName() {
        return pageName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NodeId nodeId = (NodeId) o;
        return Objects.equals(eventId, nodeId.eventId) && Objects.equals(pageName, nodeId.pageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, pageName);
    }

    @Override
    public String toString() {
        return "NodeId{" + "eventId=" + eventId + ", pageName='" + pageName + '\'' + '}';
    }

}
