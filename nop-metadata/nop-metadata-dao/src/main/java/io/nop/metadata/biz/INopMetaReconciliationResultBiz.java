
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;

import io.nop.metadata.dao.entity.NopMetaReconciliationResult;

import java.util.List;
import java.util.Map;


public interface INopMetaReconciliationResultBiz extends ICrudBiz<NopMetaReconciliationResult>{

    @BizMutation
    NopMetaReconciliationResult confirmMatch(@Name("resultId") String resultId,
                                              @Name("rowIndex") int rowIndex,
                                              @Name("selectedEntityId") String selectedEntityId,
                                              IServiceContext context);

    @BizMutation
    NopMetaReconciliationResult batchConfirmMatches(@Name("resultId") String resultId,
                                                     @Name("selections") List<Map<String, Object>> selections,
                                                     IServiceContext context);
}
