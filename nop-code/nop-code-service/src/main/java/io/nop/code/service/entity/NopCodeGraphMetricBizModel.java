
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.code.biz.INopCodeGraphMetricBiz;
import io.nop.code.dao.entity.NopCodeGraphMetric;

@BizModel("NopCodeGraphMetric")
public class NopCodeGraphMetricBizModel extends CrudBizModel<NopCodeGraphMetric> implements INopCodeGraphMetricBiz{
    public NopCodeGraphMetricBizModel(){
        setEntityName(NopCodeGraphMetric.class.getName());
    }
}
