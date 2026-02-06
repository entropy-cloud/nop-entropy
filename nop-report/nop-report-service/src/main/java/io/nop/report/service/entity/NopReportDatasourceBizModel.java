
package io.nop.report.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.report.dao.entity.NopReportDatasource;
import io.nop.report.biz.INopReportDatasourceBiz;

@BizModel("NopReportDatasource")
public class NopReportDatasourceBizModel extends CrudBizModel<NopReportDatasource> implements INopReportDatasourceBiz {
    public NopReportDatasourceBizModel(){
        setEntityName(NopReportDatasource.class.getName());
    }
}
