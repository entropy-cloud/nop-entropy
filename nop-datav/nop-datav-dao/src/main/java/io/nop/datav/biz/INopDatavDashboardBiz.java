
package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.orm.biz.ICrudBiz;
import io.nop.core.context.IServiceContext;

import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;

public interface INopDatavDashboardBiz extends ICrudBiz<NopDatavDashboard>{

    @BizMutation("publishDashboard")
    NopDatavDashboardSnapshot publishDashboard(@Name("id") String id, IServiceContext context);

    @BizQuery("getPublishedDashboard")
    NopDatavDashboardSnapshot getPublishedDashboard(@Name("id") String id, IServiceContext context);

    @BizMutation("rollbackDashboard")
    NopDatavDashboardSnapshot rollbackDashboard(@Name("id") String id, @Name("snapshotVersion") long snapshotVersion,
                                                IServiceContext context);
}
