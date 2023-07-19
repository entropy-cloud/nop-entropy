package io.nop.tcc.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.TccStatus;
import io.nop.tcc.dao.entity._gen._NopTccRecord;


@BizObjName("NopTccRecord")
public class NopTccRecord extends _NopTccRecord implements ITccRecord {
    public NopTccRecord() {
    }


    @Override
    public TccStatus getTccStatus() {
        Integer status = getStatus();
        if (status == null)
            return null;
        return TccStatus.fromCode(status);
    }
}
