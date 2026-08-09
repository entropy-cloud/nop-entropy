
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.dao.entity.NopDatavPanel;

@BizModel("NopDatavPanel")
public class NopDatavPanelBizModel extends CrudBizModel<NopDatavPanel> implements INopDatavPanelBiz {
    public NopDatavPanelBizModel() {
        setEntityName(NopDatavPanel.class.getName());
    }
}
