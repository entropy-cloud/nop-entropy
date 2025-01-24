package io.nop.sys.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.beans.ApiRequest;
import io.nop.sys.dao.entity._gen._NopSysEvent;
import io.nop.sys.dao.message.SysEventHelper;

import java.util.Map;


@BizObjName("NopSysEvent")
public class NopSysEvent extends _NopSysEvent {

    public NopSysEvent() {

    }

    public ApiRequest<Map<String, Object>> toApiRequest() {
        return SysEventHelper.fromSysEvent(this);
    }

    public void incRetryTimes() {
        Integer retryTimes = this.getRetryTimes();
        if (retryTimes == null) {
            retryTimes = 0;
        }
        setRetryTimes(retryTimes + 1);
    }
}
