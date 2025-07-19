
package io.nop.report.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.report.dao.entity.NopReportDatasetRef;

@BizModel("NopReportDatasetRef")
public class NopReportDatasetRefBizModel extends CrudBizModel<NopReportDatasetRef>{
    public NopReportDatasetRefBizModel(){
        setEntityName(NopReportDatasetRef.class.getName());
    }
}
