
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.datav.biz.INopDatavDatasetRefBiz;
import io.nop.datav.dao.entity.NopDatavDatasetRef;

@BizModel("NopDatavDatasetRef")
public class NopDatavDatasetRefBizModel extends CrudBizModel<NopDatavDatasetRef>
        implements INopDatavDatasetRefBiz {
    public NopDatavDatasetRefBizModel() {
        setEntityName(NopDatavDatasetRef.class.getName());
    }
}
