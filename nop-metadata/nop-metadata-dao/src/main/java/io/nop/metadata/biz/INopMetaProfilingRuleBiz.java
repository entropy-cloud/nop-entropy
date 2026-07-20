
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaProfilingRule;
import io.nop.orm.biz.ICrudBiz;

import java.util.Map;


/**
 * NopMetaProfilingRule BizModel 契约接口（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>跨模块 {@code @Inject INopMetaProfilingRuleBiz} 调用入口：executeProfilingRule。
 */
public interface INopMetaProfilingRuleBiz extends ICrudBiz<NopMetaProfilingRule> {

    @BizMutation
    Map<String, Object> executeProfilingRule(@Name("profilingRuleId") String profilingRuleId,
                                              @Optional @Name("schemaPattern") String schemaPattern,
                                              IServiceContext context);
}
