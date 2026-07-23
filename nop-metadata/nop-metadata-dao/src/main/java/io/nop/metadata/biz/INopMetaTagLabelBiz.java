
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.orm.biz.ICrudBiz;

import java.util.List;

public interface INopMetaTagLabelBiz extends ICrudBiz<NopMetaTagLabel> {

    @BizMutation
    List<NopMetaTagLabel> propagateTags(@Name("entityType") String entityType,
                                        @Name("entityId") String entityId,
                                        @Optional @Name("tagId") String tagId,
                                        IServiceContext context);

    @BizMutation
    List<NopMetaTagLabel> suggestTags(@Name("entityType") String entityType,
                                      @Name("entityId") String entityId,
                                      IServiceContext context);
}
