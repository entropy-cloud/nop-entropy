
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysChangeLog;
import io.nop.sys.biz.INopSysChangeLogBiz;

@BizModel("NopSysChangeLog")
public class NopSysChangeLogBizModel extends CrudBizModel<NopSysChangeLog> implements INopSysChangeLogBiz {
    public NopSysChangeLogBizModel(){
        setEntityName(NopSysChangeLog.class.getName());
    }
}
