package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.NopMetadataErrors;

import java.util.Set;

import static io.nop.metadata.service.query.AggregationHelper.containsIgnoreCase;

public class JoinExternalSideResolver {
    private final NopMetaTable leftTable;
    private final NopMetaTable rightTable;
    private final Set<String> leftCols;
    private final Set<String> rightCols;
    private final String joinId;
    private final NopMetaTable ownerTable;

    public JoinExternalSideResolver(NopMetaTable leftTable, NopMetaTable rightTable,
                                    Set<String> leftCols, Set<String> rightCols,
                                    String joinId, NopMetaTable ownerTable) {
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
            throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_SIDE_REQUIRED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("joinId", joinId);
        }
        if (columnName == null || columnName.isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("entityFieldId", String.valueOf(columnName));
        }
        String alias;
        String endpointType;
        Set<String> cols;
        if ("left".equalsIgnoreCase(declaredSide)) {
            alias = "l";
            endpointType = String.valueOf(leftTable.getTableType());
            cols = leftCols;
        } else if ("right".equalsIgnoreCase(declaredSide)) {
            alias = "r";
            endpointType = String.valueOf(rightTable.getTableType());
            cols = rightCols;
        } else {
            throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("side", declaredSide)
                    .param("endpointTableType", "unknown")
                    .param("column", columnName).param("joinId", joinId);
        }
        if (!containsIgnoreCase(cols, columnName)) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", ownerTable.getMetaTableId())
                    .param("name", name).param("side", declaredSide)
                    .param("endpointTableType", endpointType)
                    .param("column", columnName).param("joinId", joinId);
        }
        return new AggregationContext.JoinField(columnName, alias + "." + columnName);
    }
}
