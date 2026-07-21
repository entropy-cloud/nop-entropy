
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaDataProduct;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.orm.biz.ICrudBiz;

import java.util.List;


public interface INopMetaDataProductBiz extends ICrudBiz<NopMetaDataProduct> {

    @BizMutation
    NopMetaTagLabel linkAsset(@Name("dataProductId") String dataProductId,
                              @Name("entityType") String entityType,
                              @Name("entityId") String entityId,
                              IServiceContext context);

    @BizMutation
    boolean unlinkAsset(@Name("dataProductId") String dataProductId,
                        @Name("entityType") String entityType,
                        @Name("entityId") String entityId,
                        IServiceContext context);

    @BizQuery
    List<NopMetaTagLabel> getLinkedAssets(@Name("dataProductId") String dataProductId,
                                          IServiceContext context);
}
