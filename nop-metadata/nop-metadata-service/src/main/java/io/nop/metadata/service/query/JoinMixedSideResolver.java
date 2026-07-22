package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static io.nop.metadata.service.query.AggregationHelper.containsIgnoreCase;
import static io.nop.metadata.service.query.AggregationHelper.equalsStr;

public class JoinMixedSideResolver {
    private static final Logger LOG = LoggerFactory.getLogger(JoinMixedSideResolver.class);

    private final NopMetaEntity entityEndpoint;
    private final Map<String, String> entityPropToCol;
    private final NopMetaTable tableEndpoint;
    private final Set<String> tableCols;
    private final boolean entityOnLeft;
    private final String joinId;
    private final NopMetaTable ownerTable;
    private final MetaQueryContext ctx;

    public JoinMixedSideResolver(NopMetaEntity entityEndpoint, Map<String, String> entityPropToCol,
                                 NopMetaTable tableEndpoint, Set<String> tableCols,
                                 boolean entityOnLeft, String joinId, NopMetaTable ownerTable,
                                 MetaQueryContext ctx) {
        this.entityEndpoint = entityEndpoint;
        this.entityPropToCol = entityPropToCol;
        this.tableEndpoint = tableEndpoint;
        this.tableCols = tableCols;
        this.entityOnLeft = entityOnLeft;
        this.joinId = joinId;
        this.ownerTable = ownerTable;
        this.ctx = ctx;
    }

    public Set<String> leftColumns() {
        return entityOnLeft ? entityColumnCodes() : tableCols;
    }

    public Set<String> rightColumns() {
        return entityOnLeft ? tableCols : entityColumnCodes();
    }

    private Set<String> entityColumnCodes() {
        Set<String> cols = new LinkedHashSet<>();
        if (entityPropToCol != null) {
            cols.addAll(entityPropToCol.values());
        }
        return cols;
    }

    public AggregationContext.JoinField resolve(String entityFieldId, String name, String declaredSide) {
        if (entityFieldId == null || entityFieldId.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
        }
        NopMetaEntityField field = tryLoadEntityField(entityFieldId);
        if (field != null && equalsStr(field.getMetaEntityId(), entityEndpoint.getMetaEntityId())) {
            String column = field.getColumnCode();
            if (column == null || column.isEmpty()) {
                throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", entityFieldId);
            }
            String resolvedSide = entityOnLeft ? "left" : "right";
            String alias = entityOnLeft ? "l" : "r";
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
            return new AggregationContext.JoinField(column, alias + "." + column);
        }
        if (declaredSide == null || declaredSide.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_SIDE_REQUIRED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("joinId", joinId);
        }
        String expectedSide = entityOnLeft ? "right" : "left";
        String alias;
        if (expectedSide.equalsIgnoreCase(declaredSide)) {
            alias = entityOnLeft ? "r" : "l";
        } else {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("side", declaredSide)
                    .param("endpointTableType", "entity")
                    .param("column", entityFieldId).param("joinId", joinId);
        }
        if (!containsIgnoreCase(tableCols, entityFieldId)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("side", declaredSide)
                    .param("endpointTableType", String.valueOf(tableEndpoint.getTableType()))
                    .param("column", entityFieldId).param("joinId", joinId);
        }
        return new AggregationContext.JoinField(entityFieldId, alias + "." + entityFieldId);
    }

    private NopMetaEntityField tryLoadEntityField(String entityFieldId) {
        try {
            IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
            return fieldDao.getEntityById(entityFieldId);
        } catch (Exception e) {
            LOG.warn("failed to load entity field by id: {}", entityFieldId, e);
            return null;
        }
    }
}
