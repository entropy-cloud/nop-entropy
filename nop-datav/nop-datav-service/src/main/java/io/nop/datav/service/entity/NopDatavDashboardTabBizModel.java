
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.datav.biz.INopDatavDashboardTabBiz;
import io.nop.datav.dao.entity.NopDatavDashboardTab;

@BizModel("NopDatavDashboardTab")
public class NopDatavDashboardTabBizModel extends CrudBizModel<NopDatavDashboardTab>
        implements INopDatavDashboardTabBiz {
    public NopDatavDashboardTabBizModel() {
        setEntityName(NopDatavDashboardTab.class.getName());
    }
}
