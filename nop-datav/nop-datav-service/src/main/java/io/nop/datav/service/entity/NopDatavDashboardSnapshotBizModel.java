
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.datav.biz.INopDatavDashboardSnapshotBiz;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;

@BizModel("NopDatavDashboardSnapshot")
public class NopDatavDashboardSnapshotBizModel extends CrudBizModel<NopDatavDashboardSnapshot>
        implements INopDatavDashboardSnapshotBiz {
    public NopDatavDashboardSnapshotBizModel() {
        setEntityName(NopDatavDashboardSnapshot.class.getName());
    }
}
