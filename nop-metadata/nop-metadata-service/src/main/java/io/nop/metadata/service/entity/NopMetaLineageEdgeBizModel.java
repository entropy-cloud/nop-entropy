package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaLineageEdgeBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.api.dto.LineageExtractResultDTO;
import io.nop.metadata.api.dto.LineageRecordResultDTO;
import io.nop.metadata.api.dto.RecordLineageDTO;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@BizModel("NopMetaLineageEdge")
public class NopMetaLineageEdgeBizModel extends CrudBizModel<NopMetaLineageEdge> implements INopMetaLineageEdgeBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaLineageEdgeBizModel.class);

    public static final int DEFAULT_LINEAGE_MAX_EDGES = 100_000;
    public static final int DEFAULT_LINEAGE_MAX_TABLES = 100_000;

    @InjectValue(value = "@cfg:nop.metadata.lineage.max-edges|0")
    protected int configuredMaxEdges = 0;

    @InjectValue(value = "@cfg:nop.metadata.lineage.max-tables|0")
    protected int configuredMaxTables = 0;

    private NopMetaLineageEdgeQueryAction queryAction;

    private NopMetaLineageEdgeQueryAction queryAction() {
        if (queryAction == null) {
            queryAction = new NopMetaLineageEdgeQueryAction(configuredMaxEdges, configuredMaxTables);
        }
        return queryAction;
    }

    public NopMetaLineageEdgeBizModel() {
        setEntityName(NopMetaLineageEdge.class.getName());
    }

    @BizMutation
    public LineageRecordResultDTO recordLineage(@Name("edges") List<RecordLineageDTO> edges,
                                                  IServiceContext context) {
        if (edges == null || edges.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_NO_EDGES).param("size", 0);
        }
        List<NopMetaLineageEdge> parsed = new ArrayList<>(edges.size());
        Set<String> referencedTableIds = new LinkedHashSet<>();
        for (int i = 0; i < edges.size(); i++) {
            RecordLineageDTO dto = edges.get(i);
            String sourceEntityId = dto.getSourceEntityId();
            String targetEntityId = dto.getTargetEntityId();
            if (sourceEntityId == null || sourceEntityId.isEmpty() || targetEntityId == null || targetEntityId.isEmpty()) {
                throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_TABLE_ID_MISSING).param("index", i).param("edge", dto);
            }
            referencedTableIds.add(sourceEntityId);
            referencedTableIds.add(targetEntityId);
            NopMetaLineageEdge edge = dao().newEntity();
            edge.setSourceEntityId(sourceEntityId);
            edge.setTargetEntityId(targetEntityId);
            edge.setSourceColumn(dto.getSourceColumn());
            edge.setTargetColumn(dto.getTargetColumn());
            edge.setTransformType(dto.getTransformType());
            edge.setTransformExpr(dto.getTransformExpr());
            edge.setPipelineId(dto.getPipelineId());
            Double confidence = dto.getConfidence();
            if (confidence != null) edge.setConfidence(confidence);
            String lineageSource = dto.getLineageSource();
            edge.setLineageSource(lineageSource != null ? lineageSource
                    : _NopMetadataCoreConstants.LINEAGE_SOURCE_MANUAL);
            parsed.add(edge);
        }
        Set<String> existingIds = queryAction().loadExistingTableIds(referencedTableIds, daoProvider());
        for (String id : referencedTableIds) {
            if (!existingIds.contains(id)) {
                throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_TABLE_NOT_FOUND).param("tableId", id);
            }
        }
        if (!parsed.isEmpty()) {
            dao().batchSaveEntities(parsed);
        }
        orm().flushSession();
        LineageRecordResultDTO result = new LineageRecordResultDTO();
        result.setEdgeCount(parsed.size());
        return result;
    }

    /**
     * SQL 解析失败（QueryAction 内已收集进 errors 列表）必须在 API 边界显式抛错，
     * 不允许以成功响应 + 零边返回（"无静默跳过"契约，docs-for-ai/03-modules/nop-metadata.md）。
     */
    private void checkNoParseErrors(NopMetaLineageEdgeQueryAction.LineageExtractResult r,
                                    String metaEntityId, ErrorCode errorCode) {
        if (r.errors != null && !r.errors.isEmpty()) {
            Object detail = r.errors.get(0).get("error");
            // P1-6（plan 2026-08-15-1913-3）变量形态人工归类：两调用方传
            // ERR_LINEAGE_SQL_PARSE_FAILED / ERR_COL_LINEAGE_SQL_PARSE_FAILED，
            // 两 define 描述零占位符（无必需识别性参数）——误报，映射核对完毕。
            // invariant-ok: variable-form errorCode——call-site codes declare
            // no description 占位符 (plan 2026-08-15-1913-3)
            throw new NopMetadataException(errorCode)
                    .param("metaEntityId", metaEntityId)
                    .param("error", detail != null ? detail : "");
        }
    }

    @BizMutation
    public LineageExtractResultDTO extractLineageFromSql(@Name("metaEntityId") String metaEntityId,
                                                          IServiceContext context) {
        NopMetaLineageEdgeQueryAction.LineageExtractResult r =
                queryAction().extractLineageFromSql(metaEntityId, daoProvider(), dao());
        checkNoParseErrors(r, metaEntityId, NopMetadataErrors.ERR_LINEAGE_SQL_PARSE_FAILED);
        LineageExtractResultDTO dto = new LineageExtractResultDTO();
        dto.setMetaEntityId(metaEntityId);
        dto.setEdgeCount(r.edgeCount);
        // P1-3：sourceEntitys = 已解析源实体 metaEntity ID（此前误植 r.unresolved——解析失败名单混入源表集）
        dto.setSourceEntitys(r.resolvedSourceEntitys);
        dto.setUnresolved(r.unresolved);
        dto.setErrors(r.errors);
        return dto;
    }

    @BizMutation
    public LineageExtractResultDTO extractColumnLineageFromSql(@Name("metaEntityId") String metaEntityId,
                                                                 IServiceContext context) {
        NopMetaLineageEdgeQueryAction.LineageExtractResult r =
                queryAction().extractColumnLineageFromSql(metaEntityId, daoProvider(), dao());
        checkNoParseErrors(r, metaEntityId, NopMetadataErrors.ERR_COL_LINEAGE_SQL_PARSE_FAILED);
        LineageExtractResultDTO dto = new LineageExtractResultDTO();
        dto.setMetaEntityId(metaEntityId);
        dto.setEdgeCount(r.edgeCount);
        // P1-3：列级路径此前从不填充 sourceEntitys（恒空）——上浮已计算的 resolvedSourceIds
        dto.setSourceEntitys(r.resolvedSourceEntitys);
        dto.setUnresolved(r.unresolved);
        dto.setErrors(r.errors);
        return dto;
    }

    @BizMutation
    public LineageExtractResultDTO extractMeasureLineage(@Name("metaEntityId") String metaEntityId,
                                                           IServiceContext context) {
        NopMetaLineageEdgeQueryAction.LineageExtractResult r =
                queryAction().extractMeasureLineage(metaEntityId, daoProvider(), dao());
        LineageExtractResultDTO dto = new LineageExtractResultDTO();
        dto.setMetaEntityId(metaEntityId);
        dto.setEdgeCount(r.edgeCount);
        // P1-3 裁定：指标级 sourceEntitys = 宿主表自身（自环边语义；0 条边时空列表）
        dto.setSourceEntitys(r.resolvedSourceEntitys);
        dto.setUnresolved(r.unresolved);
        dto.setErrors(r.errors);
        return dto;
    }

    @BizQuery
    public List<String> getUpstream(@Name("metaEntityId") String metaEntityId, IServiceContext context) {
        return queryAction().getUpstream(metaEntityId, dao());
    }

    @BizQuery
    public List<String> getDownstream(@Name("metaEntityId") String metaEntityId, IServiceContext context) {
        return queryAction().getDownstream(metaEntityId, dao());
    }

    @BizQuery
    public List<String> getLineagePath(@Name("sourceEntityId") String sourceEntityId,
                                        @Name("targetEntityId") String targetEntityId,
                                        IServiceContext context) {
        return queryAction().getLineagePath(sourceEntityId, targetEntityId, dao());
    }

    @BizQuery
    public List<String> getImpactAnalysis(@Name("metaEntityId") String metaEntityId,
                                           @Optional @Name("columnName") String columnName,
                                           IServiceContext context) {
        return queryAction().getImpactAnalysis(metaEntityId, columnName, dao());
    }
}
