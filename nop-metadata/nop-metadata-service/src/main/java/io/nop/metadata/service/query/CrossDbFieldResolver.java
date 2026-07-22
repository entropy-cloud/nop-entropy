package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static io.nop.metadata.service.query.AggregationHelper.containsIgnoreCase;
import static io.nop.metadata.service.query.AggregationHelper.equalsStr;
import static io.nop.metadata.service.query.AggregationHelper.resolveTableColumnNames;

public class CrossDbFieldResolver {
    private static final Logger LOG = LoggerFactory.getLogger(CrossDbFieldResolver.class);

    private final MetaJoinExecutor.Endpoint leftEp;
    private final MetaJoinExecutor.Endpoint rightEp;
    private final NopMetaTableJoin join;
    private final String joinId;
    private final NopMetaTable ownerTable;
    private final MetaQueryContext ctx;

    private final boolean entityEntity;
    private final boolean tableTable;
    private final NopMetaEntity entityEndpoint;
    private final boolean entityOnLeft;
    private final NopMetaTable tableEndpoint;
    private Set<String> tableCols;
    private Set<String> leftCols;
    private Set<String> rightCols;

    public CrossDbFieldResolver(MetaJoinExecutor.Endpoint leftEp, MetaJoinExecutor.Endpoint rightEp,
                                NopMetaTableJoin join, String joinId, NopMetaTable ownerTable, MetaQueryContext ctx) {
        this.leftEp = leftEp;
        this.rightEp = rightEp;
        this.join = join;
        this.joinId = joinId;
        this.ownerTable = ownerTable;
        this.ctx = ctx;
        this.entityEntity = leftEp.isEntity() && rightEp.isEntity();
        this.tableTable = !leftEp.isEntity() && !rightEp.isEntity();
        this.entityOnLeft = leftEp.isEntity();
        this.entityEndpoint = entityEntity ? null : (leftEp.isEntity() ? leftEp.entity : rightEp.entity);
        this.tableEndpoint = entityEntity ? null : (leftEp.isEntity() ? rightEp.table : leftEp.table);
    }

    public AggregationContext.CrossDbField resolve(String entityFieldId, String name, String declaredSide, String fieldKind) {
        if (entityEntity) {
            return resolveEntityEntity(entityFieldId, name, declaredSide);
        }
        if (tableTable) {
            return resolveTableTable(entityFieldId, name, declaredSide);
        }
        return resolveMixed(entityFieldId, name, declaredSide);
    }

    public String joinId() {
        return joinId;
    }

    private AggregationContext.CrossDbField resolveEntityEntity(String entityFieldId, String name, String declaredSide) {
        if (entityFieldId == null || entityFieldId.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
        }
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        NopMetaEntityField field = fieldDao.getEntityById(entityFieldId);
        if (field == null || field.getFieldName() == null || field.getFieldName().isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("entityFieldId", entityFieldId);
        }
        String fieldMetaEntityId = field.getMetaEntityId();
        String resolvedSide;
        if (equalsStr(fieldMetaEntityId, leftEp.entity.getMetaEntityId())) {
            resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_LEFT;
        } else if (equalsStr(fieldMetaEntityId, rightEp.entity.getMetaEntityId())) {
            resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_RIGHT;
        } else {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("entityFieldId", entityFieldId)
                    .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                    .param("leftEntityId", String.valueOf(leftEp.entity.getMetaEntityId()))
                    .param("rightEntityId", String.valueOf(rightEp.entity.getMetaEntityId()))
                    .param("joinId", joinId);
        }
        if (declaredSide != null && !declaredSide.isEmpty()
                && !declaredSide.equalsIgnoreCase(resolvedSide)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name)
                    .param("declaredSide", declaredSide)
                    .param("resolvedSide", resolvedSide)
                    .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                    .param("joinId", joinId);
        }
        return new AggregationContext.CrossDbField(resolvedSide, field.getFieldName());
    }

    private AggregationContext.CrossDbField resolveTableTable(String columnName, String name, String declaredSide) {
        if (declaredSide == null || declaredSide.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_SIDE_REQUIRED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("joinId", joinId);
        }
        if (columnName == null || columnName.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("entityFieldId", String.valueOf(columnName));
        }
        String resolvedSide;
        Set<String> cols;
        if (_NopMetadataCoreConstants.JOIN_SIDE_LEFT.equalsIgnoreCase(declaredSide)) {
            resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_LEFT;
            cols = ensureLeftCols();
        } else if (_NopMetadataCoreConstants.JOIN_SIDE_RIGHT.equalsIgnoreCase(declaredSide)) {
            resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_RIGHT;
            cols = ensureRightCols();
        } else {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("side", declaredSide)
                    .param("endpointTableType", "unknown")
                    .param("column", columnName).param("joinId", joinId);
        }
        if (!containsIgnoreCase(cols, columnName)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("side", declaredSide)
                    .param("endpointTableType", String.valueOf(
                            _NopMetadataCoreConstants.JOIN_SIDE_LEFT.equalsIgnoreCase(declaredSide)
                                    ? leftEp.table.getTableType() : rightEp.table.getTableType()))
                    .param("column", columnName).param("joinId", joinId);
        }
        return new AggregationContext.CrossDbField(resolvedSide, columnName);
    }

    private AggregationContext.CrossDbField resolveMixed(String entityFieldId, String name, String declaredSide) {
        if (entityFieldId == null || entityFieldId.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
        }
        NopMetaEntityField field = tryLoadEntityField(entityFieldId);
        if (field != null && field.getFieldName() != null && !field.getFieldName().isEmpty()
                && equalsStr(field.getMetaEntityId(), entityEndpoint.getMetaEntityId())) {
            String resolvedSide = entityOnLeft
                    ? _NopMetadataCoreConstants.JOIN_SIDE_LEFT : _NopMetadataCoreConstants.JOIN_SIDE_RIGHT;
            if (declaredSide != null && !declaredSide.isEmpty()
                    && !declaredSide.equalsIgnoreCase(resolvedSide)) {
                throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name)
                        .param("declaredSide", declaredSide)
                        .param("resolvedSide", resolvedSide)
                        .param("fieldMetaEntityId", String.valueOf(field.getMetaEntityId()))
                        .param("joinId", joinId);
            }
            return new AggregationContext.CrossDbField(resolvedSide, field.getFieldName());
        }
        if (declaredSide == null || declaredSide.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_SIDE_REQUIRED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("joinId", joinId);
        }
        String expectedSide = entityOnLeft
                ? _NopMetadataCoreConstants.JOIN_SIDE_RIGHT : _NopMetadataCoreConstants.JOIN_SIDE_LEFT;
        if (!expectedSide.equalsIgnoreCase(declaredSide)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("side", declaredSide)
                    .param("endpointTableType", "entity")
                    .param("column", entityFieldId).param("joinId", joinId);
        }
        Set<String> cols = ensureMixedTableCols();
        if (!containsIgnoreCase(cols, entityFieldId)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("side", declaredSide)
                    .param("endpointTableType", String.valueOf(tableEndpoint.getTableType()))
                    .param("column", entityFieldId).param("joinId", joinId);
        }
        return new AggregationContext.CrossDbField(declaredSide.toLowerCase(java.util.Locale.ROOT), entityFieldId);
    }

    private Set<String> ensureLeftCols() {
        if (leftCols == null) {
            leftCols = resolveTableColumnNames(leftEp.table,
                    ctx.daoProvider().daoFor(NopMetaEntityField.class), ctx);
        }
        return leftCols;
    }

    private Set<String> ensureRightCols() {
        if (rightCols == null) {
            rightCols = resolveTableColumnNames(rightEp.table,
                    ctx.daoProvider().daoFor(NopMetaEntityField.class), ctx);
        }
        return rightCols;
    }

    private Set<String> ensureMixedTableCols() {
        if (tableCols == null) {
            tableCols = resolveTableColumnNames(tableEndpoint,
                    ctx.daoProvider().daoFor(NopMetaEntityField.class), ctx);
        }
        return tableCols;
    }

    private NopMetaEntityField tryLoadEntityField(String entityFieldId) {
        try {
            return ctx.daoProvider().daoFor(NopMetaEntityField.class).getEntityById(entityFieldId);
        } catch (Exception e) {
            LOG.warn("failed to load entity field by id: {}", entityFieldId, e);
            return null;
        }
    }
}
