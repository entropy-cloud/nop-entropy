package io.nop.ai.agent.session;

import java.util.Map;
import java.util.Objects;

public class VfsEvent {

    private final String eventType;
    private final Map<String, Object> data;
    private final long timestamp;

    public VfsEvent(String eventType, Map<String, Object> data, long timestamp) {
        this.eventType = eventType;
        this.data = data;
        this.timestamp = timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VfsEvent vfsEvent = (VfsEvent) o;
        return timestamp == vfsEvent.timestamp
                && Objects.equals(eventType, vfsEvent.eventType)
                && Objects.equals(data, vfsEvent.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, data, timestamp);
    }

    @Override
    public String toString() {
        return "VfsEvent{" +
                "eventType='" + eventType + '\'' +
                ", data=" + data +
                ", timestamp=" + timestamp +
                '}';
    }
}
