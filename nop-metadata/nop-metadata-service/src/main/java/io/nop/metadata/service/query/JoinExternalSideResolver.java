package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityJoin;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import java.util.Set;

import static io.nop.metadata.service.query.AggregationHelper.containsIgnoreCase;

public class JoinExternalSideResolver {
    private final NopMetaEntity leftTable;
    private final NopMetaEntity rightTable;
    private final Set<String> leftCols;
    private final Set<String> rightCols;
    private final String joinId;
    private final NopMetaEntity ownerTable;

    public JoinExternalSideResolver(NopMetaEntity leftTable, NopMetaEntity rightTable,
                                    Set<String> leftCols, Set<String> rightCols,
                                    String joinId, NopMetaEntity ownerTable) {
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.leftCols = leftCols;
        this.rightCols = rightCols;
        this.joinId = joinId;
        this.ownerTable = ownerTable;
    }

    public Set<String> leftColumns() {
        return leftCols;
    }

    public Set<String> rightColumns() {
        return rightCols;
    }

    public AggregationContext.JoinField resolve(String columnName, String name, String declaredSide) {
        if (declaredSide == null || declaredSide.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_SIDE_REQUIRED)
                    .param("metaEntityId", ownerTable.getMetaEntityId())
                    .param("name", name).param("joinId", joinId);
        }
        if (columnName == null || columnName.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaEntityId", ownerTable.getMetaEntityId())
                    .param("name", name).param("entityFieldId", String.valueOf(columnName));
        }
        String alias;
        String endpointType;
        Set<String> cols;
        if ("left".equalsIgnoreCase(declaredSide)) {
            alias = "l";
            endpointType = String.valueOf(leftTable.getEntityKind());
            cols = leftCols;
        } else if ("right".equalsIgnoreCase(declaredSide)) {
            alias = "r";
            endpointType = String.valueOf(rightTable.getEntityKind());
            cols = rightCols;
        } else {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaEntityId", ownerTable.getMetaEntityId())
                    .param("name", name).param("side", declaredSide)
                    .param("endpointEntityKind", "unknown")
                    .param("column", columnName).param("joinId", joinId);
        }
        if (!containsIgnoreCase(cols, columnName)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaEntityId", ownerTable.getMetaEntityId())
                    .param("name", name).param("side", declaredSide)
                    .param("endpointEntityKind", endpointType)
                    .param("column", columnName).param("joinId", joinId);
        }
        return new AggregationContext.JoinField(columnName, alias + "." + columnName);
    }
}
