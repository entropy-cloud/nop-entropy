
package io.nop.report.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.report.dao.entity.NopReportDatasourceAuth;
import io.nop.report.biz.INopReportDatasourceAuthBiz;

@BizModel("NopReportDatasourceAuth")
public class NopReportDatasourceAuthBizModel extends CrudBizModel<NopReportDatasourceAuth> implements INopReportDatasourceAuthBiz {
    public NopReportDatasourceAuthBizModel(){
        setEntityName(NopReportDatasourceAuth.class.getName());
    }
}
