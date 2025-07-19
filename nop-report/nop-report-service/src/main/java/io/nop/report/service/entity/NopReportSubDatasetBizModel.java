
package io.nop.report.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.report.dao.entity.NopReportSubDataset;

@BizModel("NopReportSubDataset")
public class NopReportSubDatasetBizModel extends CrudBizModel<NopReportSubDataset>{
    public NopReportSubDatasetBizModel(){
        setEntityName(NopReportSubDataset.class.getName());
    }
}
