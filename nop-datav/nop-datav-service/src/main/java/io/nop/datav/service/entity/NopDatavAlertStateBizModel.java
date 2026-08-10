
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.datav.biz.INopDatavAlertStateBiz;
import io.nop.datav.dao.entity.NopDatavAlertState;

@BizModel("NopDatavAlertState")
public class NopDatavAlertStateBizModel extends CrudBizModel<NopDatavAlertState> implements INopDatavAlertStateBiz{
    public NopDatavAlertStateBizModel(){
        setEntityName(NopDatavAlertState.class.getName());
    }
}
