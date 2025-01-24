package io.nop.sys.dao.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiHeaders;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.sys.dao.entity.NopSysEvent;

import java.sql.Timestamp;
import java.util.Map;

import static io.nop.core.lang.json.JsonTool.parseMap;
import static io.nop.message.core.MessageCoreConstants.TOPIC_PREFIX_BROADCAST;

public class SysEventHelper {

    public static ApiRequest<Map<String, Object>> fromSysEvent(NopSysEvent event) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setSelection(FieldSelectionBeanParser.fromText(null, event.getSelection()));
        request.setHeaders(parseMap(event.getEventHeaders()));
        request.setData(parseMap(event.getEventData()));
        ApiHeaders.setBizKey(request, event.getBizKey());
        ApiHeaders.setSvcName(request, event.getBizObjName());
        ApiHeaders.setTopic(request, event.getEventTopic());
        ApiHeaders.setEventTime(request, event.getEventTime());
        ApiHeaders.setProcessTime(request, event.getProcessTime());
        return request;
    }

    public static void toSysEvent(NopSysEvent event, String topic, Object message, long eventTime) {
        event.setEventTopic(topic);
        event.setEventStatus(0);
        event.setEventTime(new Timestamp(eventTime));
        event.setBizDate(DateHelper.millisToDate(eventTime));
        event.setEventName(message.getClass().getSimpleName());
        event.setProcessTime(event.getEventTime());
        event.setIsBroadcast(topic.startsWith(TOPIC_PREFIX_BROADCAST));

        if (message instanceof ApiRequest) {
            ApiRequest<?> request = (ApiRequest<?>) message;
            String bizKey = ApiHeaders.getBizKey(request);
            String bizObjName = ApiHeaders.getSvcName(request);
            event.setBizKey(bizKey);
            event.setBizObjName(bizObjName);
            if (bizKey != null) {
                event.setPartitionIndex((int) StringHelper.shortHash(bizObjName + '|' + bizKey));
            }
            String svcAction = ApiHeaders.getSvcAction(request);
            if (svcAction != null) {
                event.setEventName(svcAction);

                if (bizObjName == null) {
                    int pos = svcAction.indexOf('_');
                    if (pos > 0) {
                        bizObjName = svcAction.substring(0, pos);
                        event.setBizObjName(bizObjName);
                    }
                }
            }
            if (request.getHeaders() != null)
                event.setEventHeaders(JsonTool.stringify(request.getHeaders()));
            if (request.getSelection() != null) {
                event.setSelection(request.getSelection().toString());
            }
            if (request.getData() != null)
                event.setEventData(JsonTool.stringify(request.getData()));
        }
    }
}
