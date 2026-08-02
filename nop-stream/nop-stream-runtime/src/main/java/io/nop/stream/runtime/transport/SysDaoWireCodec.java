/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.Map;

import io.nop.api.core.beans.ApiRequest;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;

/**
 * Wire codec for the DB-backed {@code SysDaoMessageService} backend (Stage 40).
 *
 * <p>{@code SysDaoMessageService} persists an {@code ApiRequest}'s {@code data} field
 * into {@code NopSysEvent.eventData} (via {@code SysEventHelper.toEventPayload}); a bare
 * non-{@code ApiRequest} envelope loses its body — only the class simple name is kept.
 * On subscribe the backend reconstructs an {@code ApiRequest} whose {@code data} is the
 * parsed {@code eventData} map.
 *
 * <p>This codec therefore:
 * <ul>
 *   <li><b>send</b>: wraps the envelope as {@code ApiRequest{data: envelope-as-map}},
 *       so the backend persists the full envelope body into {@code NopSysEvent}.</li>
 *   <li><b>receive</b>: reads the {@code data} map back out of the delivered
 *       {@code ApiRequest} and reconstructs the envelope.</li>
 * </ul>
 *
 * <p>The codec only references {@link ApiRequest} / {@link JsonTool} — it does NOT depend
 * on {@code SysDaoMessageService}, keeping {@code nop-stream-runtime} backend-dep-free.
 */
public final class SysDaoWireCodec implements IDataPlaneWireCodec {

    public static final SysDaoWireCodec INSTANCE = new SysDaoWireCodec();

    @Override
    public Object toWire(StreamMessageEnvelope envelope) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        // Payload control objects (barrier/watermark) are not JSON beans, so the envelope
        // is flattened to a fully-serializable map first; SysEventHelper persists it as
        // eventData and reconstructs the map on the consumer side.
        request.setData(DataPlaneWireSupport.toWireMap(envelope));
        return request;
    }

    @Override
    public StreamMessageEnvelope fromWire(Object message) {
        Object data = extractData(message);
        if (data == null) {
            return null;
        }
        if (data instanceof StreamMessageEnvelope) {
            return (StreamMessageEnvelope) data;
        }
        if (data instanceof Map) {
            return (StreamMessageEnvelope) JsonTool.jsonObjectToBean(data, StreamMessageEnvelope.class);
        }
        if (data instanceof String) {
            return JsonTool.parseBeanFromText((String) data, StreamMessageEnvelope.class);
        }
        return null;
    }

    private static Object extractData(Object message) {
        if (message instanceof ApiRequest) {
            return ((ApiRequest<?>) message).getData();
        }
        return message;
    }
}
