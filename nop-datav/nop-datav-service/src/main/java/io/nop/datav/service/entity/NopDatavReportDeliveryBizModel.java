
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.datav.biz.INopDatavReportDeliveryBiz;
import io.nop.datav.dao.entity.NopDatavReportDelivery;

@BizModel("NopDatavReportDelivery")
public class NopDatavReportDeliveryBizModel extends CrudBizModel<NopDatavReportDelivery> implements INopDatavReportDeliveryBiz{
    public NopDatavReportDeliveryBizModel(){
        setEntityName(NopDatavReportDelivery.class.getName());
    }
}
