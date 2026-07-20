
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.orm.biz.ICrudBiz;

import java.util.Map;


/**
 * NopMetaQualityScore BizModel 契约接口（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>{@code @Inject INopMetaQualityScoreBiz} 跨模块调用入口（checkpoint 自动评分触发链路）：
 * computeQualityScore。
 */
public interface INopMetaQualityScoreBiz extends ICrudBiz<NopMetaQualityScore> {

    @BizMutation
    Map<String, Object> computeQualityScore(@Name("metaTableId") String metaTableId, IServiceContext context);
}
