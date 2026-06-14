package io.nop.ai.agent.message;

import io.nop.core.lang.json.JsonTool;
import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializes and deserializes {@link AgentMessageEnvelope} to/from JSON text
 * for DB-backed transport.
 *
 * <p>The payload is an opaque {@code Object} in the in-memory envelope model.
 * For DB transport it must be JSON-serializable. This helper stores the
 * payload's runtime class name alongside its JSON representation so that
 * round-trip deserialization restores the original type.
 *
 * <p><b>Constraint:</b> DB-backed transport requires payloads to be
 * JSON-serializable. In-memory transport has no such constraint because
 * payloads are passed by reference. If a payload cannot be serialized, an
 * exception is thrown (no silent skip).
 */
public final class AgentMessageEnvelopeJson {

    private static final String FIELD_SENDER_ID = "senderId";
    private static final String FIELD_TARGET_TOPIC = "targetTopic";
    private static final String FIELD_CORRELATION_ID = "correlationId";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_PAYLOAD_CLASS_NAME = "payloadClassName";
    private static final String FIELD_PAYLOAD = "payload";

    private AgentMessageEnvelopeJson() {
    }

    /**
     * Serialize an envelope to JSON text.
     *
     * @param envelope the envelope to serialize (must not be null)
     * @return JSON text representation
     * @throws NopAiAgentException if serialization fails
     */
    public static String toJson(AgentMessageEnvelope envelope) {
        if (envelope == null) {
            throw new NopAiAgentException("AgentMessageEnvelopeJson.toJson: envelope must not be null");
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put(FIELD_SENDER_ID, envelope.getSenderId());
        map.put(FIELD_TARGET_TOPIC, envelope.getTargetTopic());
        map.put(FIELD_CORRELATION_ID, envelope.getCorrelationId());
        map.put(FIELD_KIND, envelope.getKind() != null ? envelope.getKind().name() : null);
        map.put(FIELD_TIMESTAMP, envelope.getTimestamp());

        Object payload = envelope.getPayload();
        if (payload != null) {
            map.put(FIELD_PAYLOAD_CLASS_NAME, payload.getClass().getName());
            map.put(FIELD_PAYLOAD, JsonTool.beanToJsonObject(payload));
        } else {
            map.put(FIELD_PAYLOAD_CLASS_NAME, null);
            map.put(FIELD_PAYLOAD, null);
        }

        try {
            return JsonTool.stringify(map);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "AgentMessageEnvelopeJson.toJson: failed to serialize envelope: " + e.getMessage(), e);
        }
    }

    /**
     * Deserialize an envelope from JSON text.
     *
     * @param json the JSON text (must not be null or blank)
     * @return the reconstructed envelope
     * @throws NopAiAgentException if deserialization fails
     */
    public static AgentMessageEnvelope fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new NopAiAgentException("AgentMessageEnvelopeJson.fromJson: json must not be null or blank");
        }

        Map<String, Object> map;
        try {
            Object parsed = JsonTool.parseNonStrict(json);
            if (!(parsed instanceof Map)) {
                throw new NopAiAgentException(
                        "AgentMessageEnvelopeJson.fromJson: expected JSON object, got: " + parsed.getClass().getName());
            }
            map = (Map<String, Object>) parsed;
        } catch (NopAiAgentException e) {
            throw e;
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "AgentMessageEnvelopeJson.fromJson: failed to parse JSON: " + e.getMessage(), e);
        }

        String senderId = (String) map.get(FIELD_SENDER_ID);
        String targetTopic = (String) map.get(FIELD_TARGET_TOPIC);
        String correlationId = (String) map.get(FIELD_CORRELATION_ID);

        String kindName = (String) map.get(FIELD_KIND);
        AgentMessageKind kind;
        try {
            kind = kindName != null ? AgentMessageKind.valueOf(kindName) : null;
        } catch (IllegalArgumentException e) {
            throw new NopAiAgentException(
                    "AgentMessageEnvelopeJson.fromJson: unknown AgentMessageKind: " + kindName, e);
        }
        if (kind == null) {
            throw new NopAiAgentException("AgentMessageEnvelopeJson.fromJson: kind must not be null");
        }

        long timestamp = 0L;
        Object tsValue = map.get(FIELD_TIMESTAMP);
        if (tsValue instanceof Number) {
            timestamp = ((Number) tsValue).longValue();
        }

        String payloadClassName = (String) map.get(FIELD_PAYLOAD_CLASS_NAME);
        Object payloadObj = map.get(FIELD_PAYLOAD);
        Object payload = deserializePayload(payloadObj, payloadClassName);

        return new AgentMessageEnvelope(senderId, targetTopic, correlationId, kind, payload, timestamp);
    }

    private static Object deserializePayload(Object payloadObj, String payloadClassName) {
        if (payloadClassName == null || payloadObj == null) {
            return null;
        }

        Class<?> payloadClass;
        try {
            payloadClass = Class.forName(payloadClassName);
        } catch (ClassNotFoundException e) {
            throw new NopAiAgentException(
                    "AgentMessageEnvelopeJson.fromJson: payload class not found: " + payloadClassName, e);
        }

        if (payloadClass == String.class) {
            return payloadObj.toString();
        }
        if (payloadClass == Integer.class || payloadClass == int.class) {
            return ((Number) payloadObj).intValue();
        }
        if (payloadClass == Long.class || payloadClass == long.class) {
            return ((Number) payloadObj).longValue();
        }
        if (payloadClass == Double.class || payloadClass == double.class) {
            return ((Number) payloadObj).doubleValue();
        }
        if (payloadClass == Float.class || payloadClass == float.class) {
            return ((Number) payloadObj).floatValue();
        }
        if (payloadClass == Boolean.class || payloadClass == boolean.class) {
            return payloadObj;
        }

        if (Map.class.isAssignableFrom(payloadClass) || Collection.class.isAssignableFrom(payloadClass)) {
            return payloadObj;
        }

        try {
            return JsonTool.jsonObjectToBean(payloadObj, payloadClass);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "AgentMessageEnvelopeJson.fromJson: failed to deserialize payload of type "
                            + payloadClassName + ": " + e.getMessage(), e);
        }
    }
}
