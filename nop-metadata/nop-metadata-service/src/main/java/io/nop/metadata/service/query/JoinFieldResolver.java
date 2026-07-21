package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.nop.metadata.service.query.AggregationHelper.equalsStr;

public class JoinFieldResolver {
    private final String leftEntityId;
    private final String rightEntityId;
    private final String joinId;
    private final NopMetaTable table;
    private final MetaQueryContext ctx;

    public JoinFieldResolver(String leftEntityId, String rightEntityId, String joinId,
                             NopMetaTable table, MetaQueryContext ctx) {
        this.leftEntityId = leftEntityId;
        this.rightEntityId = rightEntityId;
        this.joinId = joinId;
        this.table = table;
        this.ctx = ctx;
    }

    public AggregationContext.JoinField resolve(String entityFieldId, String name, String declaredSide, String refKind) {
        if (entityFieldId == null || entityFieldId.isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
        }
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        NopMetaEntityField field = fieldDao.getEntityById(entityFieldId);
        if (field == null || field.getColumnCode() == null) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name).param("entityFieldId", entityFieldId);
        }
        String fieldMetaEntityId = field.getMetaEntityId();
        String column = field.getColumnCode();
        String resolvedSide;
        String alias;
        if (equalsStr(fieldMetaEntityId, leftEntityId)) {
            resolvedSide = "left";
            alias = "l";
        } else if (equalsStr(fieldMetaEntityId, rightEntityId)) {
            resolvedSide = "right";
            alias = "r";
        } else {
            throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name).param("entityFieldId", entityFieldId)
                    .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                    .param("leftEntityId", String.valueOf(leftEntityId))
                    .param("rightEntityId", String.valueOf(rightEntityId))
                    .param("joinId", joinId);
        }
        if (declaredSide != null && !declaredSide.isEmpty()
                && !declaredSide.equalsIgnoreCase(resolvedSide)) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name)
                    .param("declaredSide", declaredSide)
                    .param("resolvedSide", resolvedSide)
                    .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                    .param("joinId", joinId);
        }
        return new AggregationContext.JoinField(column, alias + "." + column);
    }

    public String leftEntityId() {
        return leftEntityId;
    }

    public String rightEntityId() {
        return rightEntityId;
    }

    public Set<String> resolveEntityColumns(String metaEntityId) {
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, metaEntityId));
        List<NopMetaEntityField> fields = fieldDao.findAllByQuery(q);
        Set<String> cols = new LinkedHashSet<>();
        for (NopMetaEntityField f : fields) {
            if (f.getColumnCode() != null) {
                cols.add(f.getColumnCode());
            }
        }
        return cols;
    }
}
