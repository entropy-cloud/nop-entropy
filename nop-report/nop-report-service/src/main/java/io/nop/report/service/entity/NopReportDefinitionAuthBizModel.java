
package io.nop.report.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.report.dao.entity.NopReportDefinitionAuth;
import io.nop.report.biz.INopReportDefinitionAuthBiz;

@BizModel("NopReportDefinitionAuth")
public class NopReportDefinitionAuthBizModel extends CrudBizModel<NopReportDefinitionAuth> implements INopReportDefinitionAuthBiz {
    public NopReportDefinitionAuthBizModel(){
        setEntityName(NopReportDefinitionAuth.class.getName());
    }
}
