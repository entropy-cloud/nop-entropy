
package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.orm.biz.ICrudBiz;
import io.nop.core.context.IServiceContext;

import io.nop.datav.dao.entity.NopDatavDashboardShare;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;

import java.sql.Timestamp;
import java.util.List;

public interface INopDatavDashboardShareBiz extends ICrudBiz<NopDatavDashboardShare> {

    @BizMutation("createShare")
    NopDatavDashboardShare createShare(@Name("dashboardId") String dashboardId,
                                       @Name("password") String password,
                                       @Name("expireTime") Timestamp expireTime,
                                       IServiceContext context);

    @BizQuery("listShares")
    List<NopDatavDashboardShare> listShares(@Name("dashboardId") String dashboardId,
                                            IServiceContext context);

    @BizMutation("revokeShare")
    NopDatavDashboardShare revokeShare(@Name("shareId") String shareId, IServiceContext context);

    @BizMutation("toggleShare")
    NopDatavDashboardShare toggleShare(@Name("shareId") String shareId,
                                       @Name("enabled") boolean enabled,
                                       IServiceContext context);

    @BizQuery("getSharedDashboard")
    NopDatavDashboardSnapshot getSharedDashboard(@Name("shareToken") String shareToken,
                                                 @Name("password") String password,
                                                 IServiceContext context);
}
