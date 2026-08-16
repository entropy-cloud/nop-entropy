
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.datav.biz.INopDatavScreenWidgetBiz;
import io.nop.datav.dao.entity.NopDatavScreenWidget;

@BizModel("NopDatavScreenWidget")
public class NopDatavScreenWidgetBizModel extends CrudBizModel<NopDatavScreenWidget> implements INopDatavScreenWidgetBiz{
    public NopDatavScreenWidgetBizModel(){
        setEntityName(NopDatavScreenWidget.class.getName());
    }
}
