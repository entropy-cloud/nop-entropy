package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaLineageEdgeBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.core.dto.LineageExtractResultDTO;
import io.nop.metadata.core.dto.LineageRecordResultDTO;
import io.nop.metadata.core.dto.RecordLineageDTO;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
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
            throw new NopException(NopMetadataErrors.ERR_LINEAGE_NO_EDGES).param("size", 0);
        }
        List<NopMetaLineageEdge> parsed = new ArrayList<>(edges.size());
        Set<String> referencedTableIds = new LinkedHashSet<>();
        for (int i = 0; i < edges.size(); i++) {
            RecordLineageDTO dto = edges.get(i);
            String sourceTableId = dto.getSourceTableId();
            String targetTableId = dto.getTargetTableId();
            if (sourceTableId == null || sourceTableId.isEmpty() || targetTableId == null || targetTableId.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_LINEAGE_TABLE_ID_MISSING).param("index", i).param("edge", dto);
            }
            referencedTableIds.add(sourceTableId);
            referencedTableIds.add(targetTableId);
            NopMetaLineageEdge edge = dao().newEntity();
            edge.setSourceTableId(sourceTableId);
            edge.setTargetTableId(targetTableId);
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
                throw new NopException(NopMetadataErrors.ERR_LINEAGE_TABLE_NOT_FOUND).param("tableId", id);
            }
        }
        for (NopMetaLineageEdge edge : parsed) {
            dao().saveEntity(edge);
        }
        orm().flushSession();
        LineageRecordResultDTO result = new LineageRecordResultDTO();
        result.setEdgeCount(parsed.size());
        return result;
    }

    @BizMutation
    public LineageExtractResultDTO extractLineageFromSql(@Name("metaTableId") String metaTableId,
                                                          IServiceContext context) {
        NopMetaLineageEdgeQueryAction.LineageExtractResult r =
                queryAction().extractLineageFromSql(metaTableId, daoProvider(), dao());
        LineageExtractResultDTO dto = new LineageExtractResultDTO();
        dto.setMetaTableId(metaTableId);
        dto.setEdgeCount(r.edgeCount);
        dto.setSourceTables(r.unresolved);
        dto.setUnresolved(r.unresolved);
        dto.setErrors(r.errors);
        return dto;
    }

    @BizMutation
    public LineageExtractResultDTO extractColumnLineageFromSql(@Name("metaTableId") String metaTableId,
                                                                IServiceContext context) {
        NopMetaLineageEdgeQueryAction.LineageExtractResult r =
                queryAction().extractColumnLineageFromSql(metaTableId, daoProvider(), dao());
        LineageExtractResultDTO dto = new LineageExtractResultDTO();
        dto.setMetaTableId(metaTableId);
        dto.setEdgeCount(r.edgeCount);
        dto.setUnresolved(r.unresolved);
        dto.setErrors(r.errors);
        return dto;
    }

    @BizMutation
    public LineageExtractResultDTO extractMeasureLineage(@Name("metaTableId") String metaTableId,
                                                          IServiceContext context) {
        NopMetaLineageEdgeQueryAction.LineageExtractResult r =
                queryAction().extractMeasureLineage(metaTableId, daoProvider(), dao());
        LineageExtractResultDTO dto = new LineageExtractResultDTO();
        dto.setMetaTableId(metaTableId);
        dto.setEdgeCount(r.edgeCount);
        dto.setUnresolved(r.unresolved);
        dto.setErrors(r.errors);
        return dto;
    }

    @BizQuery
    public List<String> getUpstream(@Name("metaTableId") String metaTableId, IServiceContext context) {
        return queryAction().getUpstream(metaTableId, dao());
    }

    @BizQuery
    public List<String> getDownstream(@Name("metaTableId") String metaTableId, IServiceContext context) {
        return queryAction().getDownstream(metaTableId, dao());
    }

    @BizQuery
    public List<String> getLineagePath(@Name("sourceTableId") String sourceTableId,
                                        @Name("targetTableId") String targetTableId,
                                        IServiceContext context) {
        return queryAction().getLineagePath(sourceTableId, targetTableId, dao());
    }

    @BizQuery
    public List<String> getImpactAnalysis(@Name("metaTableId") String metaTableId,
                                           @Optional @Name("columnName") String columnName,
                                           IServiceContext context) {
        return queryAction().getImpactAnalysis(metaTableId, columnName, dao());
    }
}
