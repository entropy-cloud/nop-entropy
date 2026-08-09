
package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.orm.biz.ICrudBiz;
import io.nop.core.context.IServiceContext;

import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;

import java.util.Map;

public interface INopDatavDashboardBiz extends ICrudBiz<NopDatavDashboard>{

    @BizMutation("publishDashboard")
    NopDatavDashboardSnapshot publishDashboard(@Name("id") String id, IServiceContext context);

    @BizQuery("getPublishedDashboard")
    NopDatavDashboardSnapshot getPublishedDashboard(@Name("id") String id, IServiceContext context);

    @BizMutation("rollbackDashboard")
    NopDatavDashboardSnapshot rollbackDashboard(@Name("id") String id, @Name("snapshotVersion") long snapshotVersion,
                                                IServiceContext context);

    @BizQuery("resolveFilterValues")
    Map<String, Object> resolveFilterValues(@Name("id") String id,
                                            @Name("filterValues") Map<String, Object> filterValues,
                                            IServiceContext context);

    @BizQuery("parseFilterFromUrl")
    Map<String, Object> parseFilterFromUrl(@Name("id") String id, @Name("url") String url,
                                           IServiceContext context);
}
