
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.orm.biz.ICrudBiz;

import java.util.Map;


/**
 * NopMetaQualityCheckpoint BizModel 契约接口（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>{@code @Inject INopMetaQualityCheckpointBiz} 跨模块调用入口（B2 方案 b cron 触发链路用）：
 * executeCheckpoint。
 */
public interface INopMetaQualityCheckpointBiz extends ICrudBiz<NopMetaQualityCheckpoint> {

    @BizMutation
    Map<String, Object> executeCheckpoint(@Name("checkpointId") String checkpointId,
                                          @Optional @Name("schemaPattern") String schemaPattern,
                                          IServiceContext context);
}
