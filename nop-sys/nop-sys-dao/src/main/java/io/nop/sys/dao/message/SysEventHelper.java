package io.nop.sys.dao.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiHeaders;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.sys.dao.entity.NopSysBroadcastEvent;
import io.nop.sys.dao.entity.NopSysEvent;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static io.nop.core.lang.json.JsonTool.parseMap;
import static io.nop.message.core.MessageCoreConstants.TOPIC_PREFIX_BROADCAST;

public class SysEventHelper {

    public static final int DEFAULT_PARTITION_INDEX = 0;

    public static ApiRequest<Map<String, Object>> fromSysEvent(NopSysEvent event) {
        return buildApiRequest(event.getSelection(), event.getEventHeaders(), event.getEventData(), event.getBizKey(),
                event.getBizObjName(), event.getEventTopic(), event.getEventTime(), event.getProcessTime());
    }

    public static ApiRequest<Map<String, Object>> fromBroadcastEvent(NopSysBroadcastEvent event) {
        return buildApiRequest(event.getSelection(), event.getEventHeaders(), event.getEventData(), event.getBizKey(),
                event.getBizObjName(), event.getEventTopic(), event.getEventTime(), event.getEventTime());
    }

    private static ApiRequest<Map<String, Object>> buildApiRequest(String selection, String eventHeaders,
                                                                   String eventData, String bizKey,
                                                                   String bizObjName, String topic,
                                                                   Timestamp eventTime, Timestamp processTime) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setSelection(FieldSelectionBeanParser.fromText(null, selection));
        request.setHeaders(parseJsonMap(eventHeaders));
        request.setData(parseJsonMap(eventData));
        ApiHeaders.setBizKey(request, bizKey);
        ApiHeaders.setSvcName(request, bizObjName);
        ApiHeaders.setTopic(request, topic);
        ApiHeaders.setEventTime(request, eventTime);
        ApiHeaders.setProcessTime(request, processTime);
        return request;
    }

    public static void toSysEvent(NopSysEvent event, String topic, Object message, long eventTime) {
        EventPayload payload = toEventPayload(topic, message, eventTime);
        fillEventFields(event, payload);
        event.setEventStatus(0);
        event.setProcessTime(event.getEventTime());
        event.setScheduleTime(event.getEventTime());
        event.setIsBroadcast(topic.startsWith(TOPIC_PREFIX_BROADCAST));
        event.setPartitionIndex(resolvePartitionIndex(payload));
        event.setRetryTimes(0);
    }

    public static void toBroadcastEvent(NopSysBroadcastEvent event, String topic, Object message, long eventTime) {
        EventPayload payload = toEventPayload(topic, message, eventTime);
        fillEventFields(event, payload);
    }

    private static int resolvePartitionIndex(EventPayload payload) {
        if (!StringHelper.isEmpty(payload.bizObjName) && !StringHelper.isEmpty(payload.bizKey)) {
            return StringHelper.shortHash(payload.bizObjName + '|' + payload.bizKey);
        }
        if (!StringHelper.isEmpty(payload.bizKey)) {
            return StringHelper.shortHash(payload.bizKey);
        }
        if (!StringHelper.isEmpty(payload.topic)) {
            return StringHelper.shortHash(payload.topic);
        }
        return DEFAULT_PARTITION_INDEX;
    }

    private static EventPayload toEventPayload(String topic, Object message, long eventTime) {
        EventPayload payload = new EventPayload();
        payload.topic = topic;
        payload.eventTime = new Timestamp(eventTime);
        payload.bizDate = DateHelper.millisToDate(eventTime);
        payload.eventName = message == null ? "NullMessage" : message.getClass().getSimpleName();
        payload.eventHeaders = JsonTool.stringify(Collections.emptyMap());
        payload.eventData = JsonTool.stringify(Collections.emptyMap());

        if (message instanceof ApiRequest) {
            ApiRequest<?> request = (ApiRequest<?>) message;
            payload.bizKey = ApiHeaders.getBizKey(request);
            payload.bizObjName = ApiHeaders.getSvcName(request);

            String svcAction = ApiHeaders.getSvcAction(request);
            if (svcAction != null) {
                payload.eventName = svcAction;
                if (payload.bizObjName == null) {
                    int pos = svcAction.indexOf('_');
                    if (pos > 0) {
                        payload.bizObjName = svcAction.substring(0, pos);
                    }
                }
            }
            if (request.hasHeaders()) {
                payload.eventHeaders = JsonTool.stringify(request.getHeaders());
            }
            if (request.getSelection() != null) {
                payload.selection = request.getSelection().toString();
            }
            if (request.getData() != null) {
                payload.eventData = JsonTool.stringify(request.getData());
            }
        }
        return payload;
    }

    private static void fillEventFields(NopSysEvent event, EventPayload payload) {
        event.setEventTopic(payload.topic);
        event.setEventTime(payload.eventTime);
        event.setBizDate(payload.bizDate);
        event.setEventName(payload.eventName);
        event.setBizKey(payload.bizKey);
        event.setBizObjName(payload.bizObjName);
        event.setEventHeaders(payload.eventHeaders);
        event.setEventData(payload.eventData);
        event.setSelection(payload.selection);
    }

    private static void fillEventFields(NopSysBroadcastEvent event, EventPayload payload) {
        event.setEventTopic(payload.topic);
        event.setEventTime(payload.eventTime);
        event.setBizDate(payload.bizDate);
        event.setEventName(payload.eventName);
        event.setBizKey(payload.bizKey);
        event.setBizObjName(payload.bizObjName);
        event.setEventHeaders(payload.eventHeaders);
        event.setEventData(payload.eventData);
        event.setSelection(payload.selection);
    }

    private static Map<String, Object> parseJsonMap(String text) {
        if (StringHelper.isEmpty(text)) {
            return Collections.emptyMap();
        }
        return parseMap(text);
    }

    private static final class EventPayload {
        private String topic;
        private String eventName;
        private String eventHeaders;
        private String eventData;
        private String selection;
        private String bizObjName;
        private String bizKey;
        private Timestamp eventTime;
        private LocalDate bizDate;
    }
}
