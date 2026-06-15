
package io.nop.ai.biz;

import io.nop.ai.dao.dto.ModelUsageSummary;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;
import io.nop.ai.dao.entity.NopAiChatResponse;

import java.util.List;

public interface INopAiChatResponseBiz extends ICrudBiz<NopAiChatResponse> {

    @BizQuery
    List<ModelUsageSummary> summarizeByModel(@Name("sessionId") String sessionId, IServiceContext context);
}
