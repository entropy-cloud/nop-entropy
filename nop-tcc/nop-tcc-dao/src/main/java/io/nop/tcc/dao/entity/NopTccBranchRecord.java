package io.nop.tcc.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.beans.ApiRequest;
import io.nop.core.lang.json.JsonTool;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.TccStatus;
import io.nop.tcc.dao.entity._gen._NopTccBranchRecord;


@BizObjName("NopTccBranchRecord")
public class NopTccBranchRecord extends _NopTccBranchRecord implements ITccBranchRecord {
    public NopTccBranchRecord() {
    }

    @Override
    public String getTxnGroup() {
        NopTccRecord record = getTccRecord();
        return record == null ? null : record.getTxnGroup();
    }

    @Override
    public ApiRequest<?> getRequest() {
        return (ApiRequest<?>) JsonTool.parseBeanFromText(getRequestData(), ApiRequest.class);
    }

    @Override
    public TccStatus getBranchStatus() {
        Integer status = getStatus();
        if (status == null)
            return null;
        return TccStatus.fromCode(status);
    }
}
