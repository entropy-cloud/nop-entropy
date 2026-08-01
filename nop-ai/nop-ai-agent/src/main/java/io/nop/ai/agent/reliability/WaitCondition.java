package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.api.core.json.JSON;

import java.util.Map;
import java.util.Objects;

/**
 * The condition payload for a {@link CheckpointType#WAIT_FOR} checkpoint
 * (design §13.1 Decision D). A {@code WaitCondition} captures what the
 * session is waiting for and how to evaluate whether the condition is
 * satisfied.
 *
 * <p><b>First-version condition type set</b>:
 * <ul>
 *   <li>{@link Type#TIMEOUT} — satisfied when {@code now >= deadlineMs}.</li>
 *   <li>{@link Type#EVENT} — satisfied by an external {@code deliverWake} call.</li>
 *   <li>{@link Type#USER_INPUT} — satisfied by an external {@code deliverWake}
 *       call (semantically for user input).</li>
 * </ul>
 *
 * <p><b>JSON schema</b>: serialized as inline JSON in the
 * {@code Checkpoint.wait_for} column (CLOB). Example:
 * {@code {"type":"event","key":"user-approval"}}.
 */
public final class WaitCondition {

    public enum Type {
        TIMEOUT,
        EVENT,
        USER_INPUT
    }

    private final Type type;
    private final long deadlineMs;
    private final String key;

    private WaitCondition(Type type, long deadlineMs, String key) {
        this.type = type;
        this.deadlineMs = deadlineMs;
        this.key = key;
    }

    public static WaitCondition timeout(long deadlineMs) {
        return new WaitCondition(Type.TIMEOUT, deadlineMs, null);
    }

    public static WaitCondition event(String key) {
        return new WaitCondition(Type.EVENT, 0L, key);
    }

    public static WaitCondition userInput(String key) {
        return new WaitCondition(Type.USER_INPUT, 0L, key);
    }

    public Type getType() {
        return type;
    }

    public long getDeadlineMs() {
        return deadlineMs;
    }

    public String getKey() {
        return key;
    }

    /**
     * Serialize this condition to a JSON string for storage in the
     * {@code wait_for} column.
     */
    public String toJsonString() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("type", type.name());
        if (type == Type.TIMEOUT) {
            map.put("deadlineMs", deadlineMs);
        } else {
            map.put("key", key != null ? key : "");
        }
        return JSON.stringify(map);
    }

    /**
     * Deserialize a condition from a JSON string (the {@code wait_for} column
     * value). Returns {@code null} if the input is {@code null} or empty.
     */
    @SuppressWarnings("unchecked")
    public static WaitCondition fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        Map<String, Object> map;
        try {
            map = (Map<String, Object>) JSON.parse(json);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "WaitCondition.fromJson: failed to parse JSON: " + json, e);
        }
        if (map == null) {
            return null;
        }
        String typeStr = Objects.toString(map.get("type"), null);
        if (typeStr == null) {
            throw new NopAiAgentException(
                    "WaitCondition.fromJson: missing 'type' field in: " + json);
        }
        Type t;
        try {
            t = Type.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new NopAiAgentException(
                    "WaitCondition.fromJson: unknown condition type '" + typeStr + "'");
        }
        switch (t) {
            case TIMEOUT:
                return timeout(((Number) map.get("deadlineMs")).longValue());
            case EVENT:
                return event(Objects.toString(map.get("key"), null));
            case USER_INPUT:
                return userInput(Objects.toString(map.get("key"), null));
            default:
                throw new NopAiAgentException("WaitCondition.fromJson: unsupported type: " + t);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WaitCondition that = (WaitCondition) o;
        return deadlineMs == that.deadlineMs
                && type == that.type
                && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, deadlineMs, key);
    }

    @Override
    public String toString() {
        return "WaitCondition{" + toJsonString() + '}';
    }
}
