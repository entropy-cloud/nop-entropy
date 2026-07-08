package io.nop.sys.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.beans.ApiRequest;
import io.nop.sys.dao.entity._gen._NopSysBroadcastEvent;
import io.nop.sys.dao.message.SysEventHelper;

import java.util.Map;

@BizObjName("NopSysBroadcastEvent")
public class NopSysBroadcastEvent extends _NopSysBroadcastEvent {

    public ApiRequest<Map<String, Object>> toApiRequest() {
        return SysEventHelper.fromBroadcastEvent(this);
    }
}
