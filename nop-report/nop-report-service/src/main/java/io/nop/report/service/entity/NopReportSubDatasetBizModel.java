
package io.nop.report.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.report.dao.entity.NopReportSubDataset;
import io.nop.report.biz.INopReportSubDatasetBiz;

@BizModel("NopReportSubDataset")
public class NopReportSubDatasetBizModel extends CrudBizModel<NopReportSubDataset> implements INopReportSubDatasetBiz {
    public NopReportSubDatasetBizModel(){
        setEntityName(NopReportSubDataset.class.getName());
    }
}
