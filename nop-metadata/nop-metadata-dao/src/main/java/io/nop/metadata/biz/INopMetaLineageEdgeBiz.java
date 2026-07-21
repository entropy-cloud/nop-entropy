
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.orm.biz.ICrudBiz;

import io.nop.metadata.core.dto.LineageExtractResultDTO;
import io.nop.metadata.core.dto.LineageRecordResultDTO;

import java.util.List;
import java.util.Map;


/**
 * NopMetaLineageEdge BizModel 契约接口（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>跨模块 {@code @Inject INopMetaLineageEdgeBiz} 调用入口：
 * recordLineage / extractLineageFromSql / extractColumnLineageFromSql / extractMeasureLineage /
 * getUpstream / getDownstream / getLineagePath / getImpactAnalysis。
 */
public interface INopMetaLineageEdgeBiz extends ICrudBiz<NopMetaLineageEdge> {

    @BizMutation
    LineageRecordResultDTO recordLineage(@Name("edges") List<Map<String, Object>> edges, IServiceContext context);

    @BizMutation
    LineageExtractResultDTO extractLineageFromSql(@Name("metaTableId") String metaTableId, IServiceContext context);

    @BizMutation
    LineageExtractResultDTO extractColumnLineageFromSql(@Name("metaTableId") String metaTableId, IServiceContext context);

    @BizMutation
    LineageExtractResultDTO extractMeasureLineage(@Name("metaTableId") String metaTableId, IServiceContext context);

    @BizQuery
    List<String> getUpstream(@Name("metaTableId") String metaTableId, IServiceContext context);

    @BizQuery
    List<String> getDownstream(@Name("metaTableId") String metaTableId, IServiceContext context);

    @BizQuery
    List<String> getLineagePath(@Name("sourceTableId") String sourceTableId,
                                 @Name("targetTableId") String targetTableId,
                                 IServiceContext context);

    @BizQuery
    List<String> getImpactAnalysis(@Name("metaTableId") String metaTableId,
                                    @Optional @Name("columnName") String columnName,
                                    IServiceContext context);
}
