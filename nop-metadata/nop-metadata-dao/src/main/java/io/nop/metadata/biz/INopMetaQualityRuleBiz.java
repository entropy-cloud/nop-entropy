
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.orm.biz.ICrudBiz;

import java.util.Map;


/**
 * NopMetaQualityRule BizModel 契约接口（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>{@code @Inject INopMetaQualityRuleBiz} 跨模块调用入口：
 * executeQualityRule / executeQualityRulesForDataSource / judgeByRuleId。
 */
public interface INopMetaQualityRuleBiz extends ICrudBiz<NopMetaQualityRule> {

    @BizMutation
    Map<String, Object> executeQualityRule(@Name("qualityRuleId") String qualityRuleId,
                                            @Optional @Name("schemaPattern") String schemaPattern,
                                            IServiceContext context);

    @BizMutation
    Map<String, Object> executeQualityRulesForDataSource(@Name("dataSourceId") String dataSourceId,
                                                           @Optional @Name("schemaPattern") String schemaPattern,
                                                           IServiceContext context);

    @BizQuery
    Map<String, Object> judgeByRuleId(@Name("ruleId") String ruleId, IServiceContext context);
}
