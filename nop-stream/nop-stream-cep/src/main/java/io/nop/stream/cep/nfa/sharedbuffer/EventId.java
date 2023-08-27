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

import java.util.Comparator;
import java.util.Objects;

/**
 * Composite key for events in {@link SharedBuffer}.
 */
public class EventId implements Comparable<EventId> {
    private final int id;
    private final long timestamp;

    public EventId(int id, long timestamp) {
        this.id = id;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public static final Comparator<EventId> COMPARATOR =
            Comparator.comparingLong(EventId::getTimestamp).thenComparingInt(EventId::getId);

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventId eventId = (EventId) o;
        return id == eventId.id && timestamp == eventId.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, timestamp);
    }

    @Override
    public String toString() {
        return "EventId{" + "id=" + id + ", timestamp=" + timestamp + '}';
    }

    @Override
    public int compareTo(EventId o) {
        return COMPARATOR.compare(this, o);
    }
}
