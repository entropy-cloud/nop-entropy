
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.orm.biz.ICrudBiz;

import io.nop.metadata.api.dto.LineageExtractResultDTO;
import io.nop.metadata.api.dto.LineageRecordResultDTO;
import io.nop.metadata.api.dto.RecordLineageDTO;

import java.util.List;


/**
 * NopMetaLineageEdge BizModel 契约接口（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>跨模块 {@code @Inject INopMetaLineageEdgeBiz} 调用入口：
 * recordLineage / extractLineageFromSql / extractColumnLineageFromSql / extractMeasureLineage /
 * getUpstream / getDownstream / getLineagePath / getImpactAnalysis。
 */
public interface INopMetaLineageEdgeBiz extends ICrudBiz<NopMetaLineageEdge> {

    @BizMutation
    LineageRecordResultDTO recordLineage(@Name("edges") List<RecordLineageDTO> edges, IServiceContext context);

    @BizMutation
    LineageExtractResultDTO extractLineageFromSql(@Name("metaEntityId") String metaEntityId, IServiceContext context);

    @BizMutation
    LineageExtractResultDTO extractColumnLineageFromSql(@Name("metaEntityId") String metaEntityId, IServiceContext context);

    @BizMutation
    LineageExtractResultDTO extractMeasureLineage(@Name("metaEntityId") String metaEntityId, IServiceContext context);

    @BizQuery
    List<String> getUpstream(@Name("metaEntityId") String metaEntityId, IServiceContext context);

    @BizQuery
    List<String> getDownstream(@Name("metaEntityId") String metaEntityId, IServiceContext context);

    @BizQuery
    List<String> getLineagePath(@Name("sourceEntityId") String sourceEntityId,
                                 @Name("targetEntityId") String targetEntityId,
                                 IServiceContext context);

    @BizQuery
    List<String> getImpactAnalysis(@Name("metaEntityId") String metaEntityId,
                                    @Optional @Name("columnName") String columnName,
                                    IServiceContext context);
}
