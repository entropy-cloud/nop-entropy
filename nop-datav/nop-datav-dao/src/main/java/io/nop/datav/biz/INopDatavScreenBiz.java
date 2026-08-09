
package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;

import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;

public interface INopDatavScreenBiz extends ICrudBiz<NopDatavScreen> {

    @BizMutation("publishScreen")
    NopDatavScreenSnapshot publishScreen(@Name("id") String id, IServiceContext context);

    @BizQuery("getPublishedScreen")
    NopDatavScreenSnapshot getPublishedScreen(@Name("id") String id, IServiceContext context);

    @BizMutation("rollbackScreen")
    NopDatavScreenSnapshot rollbackScreen(@Name("id") String id,
                                          @Name("snapshotVersion") long snapshotVersion,
                                          IServiceContext context);

    @BizQuery("getScreenLayout")
    ScreenLayoutConfig getScreenLayout(@Name("id") String id, IServiceContext context);
}
