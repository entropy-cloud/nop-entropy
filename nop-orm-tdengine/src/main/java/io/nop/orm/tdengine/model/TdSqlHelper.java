package io.nop.orm.tdengine.model;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.commons.collections.IntArray;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import java.util.Collection;
import java.util.List;

import static io.nop.orm.eql.utils.EqlHelper.appendCol;
import static io.nop.orm.sql.GenSqlHelper.appendOrderBy;
import static io.nop.orm.sql.GenSqlHelper.genSelectFields;
import static io.nop.orm.sql.GenSqlHelper.table;

public class TdSqlHelper {
    public static SQL.SqlBuilder genLoadSql(TdTableMeta tableMeta, IOrmEntity entity, IntArray propIds) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("load:" + tableMeta.getSuperTableName());
        sb.select();

        IEntityModel entityModel = tableMeta.getEntityModel();

        for (int i = 0, n = propIds.size(); i < n; i++) {
            if (i != 0) {
                sb.append(',');
            }
            IColumnModel col = entityModel.getColumnByPropId(propIds.get(i), false);
            sb.append(col.getCode());
        }

        sb.where();

        List<? extends IColumnModel> pkCols = entityModel.getPkColumns();
        for (int i = 0, n = pkCols.size(); i < n; i++) {
            IColumnModel pkCol = pkCols.get(i);
            if (i != 0) {
                sb.append(',');
            }
            sb.append(pkCol.getCode()).append('=');
            Object value = entity.orm_propValue(pkCol.getPropId());
            appendString(sb, value);
        }
        return sb;
    }

    public static SQL.SqlBuilder genBatchInsertSubTableSql(SQL.SqlBuilder sb,
                                                           TdTableMeta tableMeta, List<IOrmEntity> list) {
        genInsertSubTableSql(sb, tableMeta, list.get(0));
        for (IOrmEntity entity : list) {
            appendValues(sb, entity);
        }
        return sb;
    }

    public static SQL.SqlBuilder genInsertSubTableSql(SQL.SqlBuilder sb, TdTableMeta tableMeta, IOrmEntity entity) {
        sb.append(tableMeta.getSubTableName(entity));

        sb.append(" \nUSING ");
        sb.append(tableMeta.getSuperTableName());

        if (!tableMeta.getTagCols().isEmpty()) {
            sb.append('(');
            for (int i = 0, n = tableMeta.getTagCols().size(); i < n; i++) {
                IColumnModel col = tableMeta.getTagCols().get(i);
                if (i != 0)
                    sb.append(',');
                sb.append(col.getCode());
            }
            sb.append(')');

            sb.append(" TAGS ");
            appendColValues(sb, tableMeta.getTagCols(), entity);
        }
        return sb;
    }

    private static void appendColValues(SQL.SqlBuilder sb, List<IColumnModel> cols, IOrmEntity entity) {
        for (int i = 0, n = cols.size(); i < n; i++) {
            if (i != 0)
                sb.append(',');

            Object value = cols.get(i);
            if (value instanceof Number) {
                sb.append(value);
            } else {
                appendString(sb, value);
            }
        }
    }

    static void appendString(SQL.SqlBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else {
            String str = value.toString();
            if (str.isEmpty()) {
                sb.append("''");
            } else {
                sb.append(StringHelper.escapeSql(str, true));
            }
        }
    }

    public static void appendValues(SQL.SqlBuilder sb, IOrmEntity entity) {
        sb.append(" \nVALUES(");
        sb.append(")");
    }

    static void appendExampleFilter(SQL.SqlBuilder sb, IDialect dialect, String owner, IEntityModel entityModel,
                                    IDataParameterBinder[] binders, IOrmEntity example) {
        example.orm_forEachInitedProp((value, propId) -> {
            sb.and();
            IColumnModel col = entityModel.getColumnByPropId(propId, false);
            appendEq(sb, dialect, owner, col, binders[col.getPropId()], value);
        });

        if (entityModel.isUseTenant() && entityModel.getTenantPropId() > 0) {
            sb.and();
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getTenantPropId(), false);
            appendEq(sb, dialect, owner, col, binders[col.getPropId()], ContextProvider.currentTenantId());
        }

        if (entityModel.isUseRevision() && entityModel.getNopRevEndVarPropId() > 0) {
            sb.and();
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getNopRevEndVarPropId(), false);
            appendEq(sb, dialect, owner, col, binders[col.getPropId()], OrmConstants.NOP_VER_MAX_VALUE);
        }
    }

    public static void appendEq(SQL.SqlBuilder sb, IDialect dialect, String owner, IColumnModel col,
                                IDataParameterBinder binder, Object value) {
        appendCol(sb, dialect, owner, col);
        sb.append("=");
        appendString(sb, value);
    }

    public static SQL.SqlBuilder genLoadSqlPart(IDialect dialect, IEntityModel entityModel, IntArray propIds) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("batchLoad:" + entityModel.getName());
        sb.select();
        genSelectFields(sb, dialect, null, entityModel, propIds);
        sb.append('\n');
        sb.from();
        table(sb, dialect, entityModel, null);
        sb.append('\n');
        sb.where();
        return sb;
    }

    public static void appendBatchLoadEq(SQL.SqlBuilder sb, IDialect dialect, IEntityModel entityModel,
                                         IDataParameterBinder[] binders, Collection<IOrmEntity> entities) {
        sb.append('\n');
        IColumnModel tenantCol = null;
        if (entityModel.isUseTenant()) {
            tenantCol = entityModel.getColumnByPropId(entityModel.getTenantPropId(), false);
            appendEq(sb, dialect, null, tenantCol, binders[tenantCol.getPropId()], ContextProvider.currentTenantId());
            sb.and();
        }

        sb.append('(');
        int i = 0;
        for (IOrmEntity entity : entities) {
            if (i != 0) {
                sb.or();
            }
            appendEntityEk(sb, entityModel, entity);
            i++;
        }
        sb.append(')');
    }

    static void appendEntityEk(SQL.SqlBuilder sb, IEntityModel entityModel, IOrmEntity entity) {
        List<? extends IColumnModel> cols = entityModel.getPkColumns();
        for (int i = 0, n = cols.size(); i < n; i++) {
            if (i != 0)
                sb.and();
            IColumnModel pkCol = cols.get(i);
            sb.append(pkCol.getCode()).append('=');
            Object value = entity.orm_propValue(pkCol.getPropId());
            appendString(sb, value);
        }
    }

    public static SQL.SqlBuilder genCountByExample(IDialect dialect, IEntityModel entityModel,
                                                   IDataParameterBinder[] binders, IOrmEntity example) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("countByExample:" + entityModel.getName());
        sb.select().append(" count(1) ").from();
        table(sb, dialect, entityModel, null);
        sb.where().alwaysTrue();
        appendExampleFilter(sb, dialect, null, entityModel, binders, example);
        return sb;
    }

    public static SQL.SqlBuilder genDeleteByExample(IDialect dialect, IEntityModel entityModel,
                                                    IDataParameterBinder[] binders, String owner, IOrmEntity example) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("deleteByExample:" + entityModel.getName());
        sb.deleteFrom();
        table(sb, dialect, entityModel, "o");
        sb.where().alwaysTrue();
        appendExampleFilter(sb, dialect, owner, entityModel, binders, example);
        return sb;
    }

    public static SQL.SqlBuilder genBatchDelete(IDialect dialect, IEntityModel entityModel,
                                                IDataParameterBinder[] binders, Collection<IOrmEntity> entities) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("batchDelete:" + entityModel.getName());
        sb.deleteFrom();
        table(sb, dialect, entityModel, null);
        sb.where().alwaysTrue();
        appendBatchLoadEq(sb, dialect, entityModel, binders, entities);
        return sb;
    }

    public static SQL.SqlBuilder genFindByExample(IDialect dialect, IEntityModel entityModel,
                                                  IDataParameterBinder[] binders, IOrmEntity example, List<OrderFieldBean> orderBy) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("findByExample:" + entityModel.getName());
        sb.select();
        genSelectFields(sb, dialect, "o", entityModel, entityModel.getEagerLoadProps());
        sb.from();
        table(sb, dialect, entityModel, "o");

        sb.where().alwaysTrue();
        appendExampleFilter(sb, dialect, "o", entityModel, binders, example);
        appendOrderBy(sb, dialect, "o", entityModel, orderBy);
        return sb;
    }
}
