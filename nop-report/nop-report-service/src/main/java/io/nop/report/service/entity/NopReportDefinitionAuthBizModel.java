
package io.nop.report.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.report.dao.entity.NopReportDefinitionAuth;

@BizModel("NopReportDefinitionAuth")
public class NopReportDefinitionAuthBizModel extends CrudBizModel<NopReportDefinitionAuth>{
    public NopReportDefinitionAuthBizModel(){
        setEntityName(NopReportDefinitionAuth.class.getName());
    }
}
