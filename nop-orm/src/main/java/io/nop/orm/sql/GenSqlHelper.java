/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.ImmutableIntArray;
import io.nop.commons.collections.IntArray;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.mutable.MutableInt;
import io.nop.commons.text.marker.MarkedString;
import io.nop.commons.text.marker.Markers;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SyntaxMarker;
import io.nop.core.lang.sql.TypedValueMarker;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.lock.LockOption;
import io.nop.dao.shard.ShardSelection;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmConstants;
import io.nop.orm.eql.OrmEqlConstants;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.support.OrmEntityHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_PROP_NOT_UPDATABLE;
import static io.nop.orm.eql.utils.EqlHelper.appendCol;

public class GenSqlHelper {

    public static EntitySQL genLoadSql(IDialect dialect, IEntityModel entityModel, IDataParameterBinder[] binders,
                                       IntArray propIds) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("load:" + entityModel.getName());
        sb.querySpace(entityModel.getQuerySpace());

        sb.select();
        genSelectFields(sb, dialect, null, entityModel, propIds);
        sb.append('\n');
        sb.from();
        table(sb, dialect, entityModel, null);
        sb.append('\n');
        sb.where();
        MutableIntArray params = new MutableIntArray(entityModel.getPkColumns().size() + 2);
        genEntityFilter(params, sb, dialect, null, entityModel, binders);
        return new EntitySQL(sb.end(), propIds, params);
    }

    public static EntitySQL genLoadSqlPart(IDialect dialect, IEntityModel entityModel, IntArray propIds) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("batchLoad:" + entityModel.getName());
        sb.querySpace(entityModel.getQuerySpace());

        sb.select();
        genSelectFields(sb, dialect, null, entityModel, propIds);
        sb.append('\n');
        sb.from();
        table(sb, dialect, entityModel, null);
        sb.append('\n');
        sb.where();
        return new EntitySQL(sb.end(), propIds, ImmutableIntArray.EMPTY);
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

        addFixedFilter(false, sb, dialect, null, entityModel, binders);

        int paramCount = 0;

        int cnt = entityModel.getPkColumnNames().size();
        if (cnt == 1) {
            IColumnModel col = (IColumnModel) entityModel.getIdProp();
            appendCol(sb, dialect, null, col);
            sb.append(" in (");
            for (IOrmEntity entity : entities) {
                Object value = entity.orm_propValue(col.getPropId());
                value = cast(entity, col, value);
                sb.param(value);
                if (paramCount % 10 == 9)
                    sb.append('\n');
                paramCount++;
                sb.append(',');
            }
            sb.deleteTail(1);
            sb.append(")");
        } else {
            sb.append('(');
            for (IColumnModel col : entityModel.getPkColumns()) {
                appendCol(sb, dialect, null, col);
                sb.append(',');
            }
            sb.deleteTail(1);
            sb.append(')');
            sb.append(" in (");
            for (IOrmEntity entity : entities) {
                appendEntityPk(sb, entityModel, entity);
                if (paramCount % 10 == 9)
                    sb.append('\n');
                paramCount++;
                sb.append(',');
            }
            sb.deleteTail(1);
            sb.append(")");
        }
    }

    static void appendEntityPk(SQL.SqlBuilder sb, IEntityModel entityModel, IOrmEntity entity) {
        sb.append('(');
        for (IColumnModel col : entityModel.getPkColumns()) {
            Object value = entity.orm_propValue(col.getPropId());
            value = cast(entity, col, value);
            sb.param(value);
            sb.append(',');
        }
        sb.deleteTail(1);
        sb.append(')');
    }

    // static IColumnModel getPkColumnNotTenantId(IEntityModel entityModel) {
    // IEntityPropModel prop = entityModel.getIdProp();
    // if (prop.isSingleColumn())
    // return (IColumnModel) prop;
    //
    // for (IColumnModel col : entityModel.getPkColumns()) {
    // if (col.getPropId() != entityModel.getTenantPropId())
    // return col;
    // }
    // throw new IllegalStateException("invalid");
    // }

    public static EntitySQL genDeleteSql(IDialect dialect, IEntityModel entityModel, IDataParameterBinder[] binders) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("delete:" + entityModel.getName());
        sb.querySpace(entityModel.getQuerySpace());

        sb.deleteFrom();
        table(sb, dialect, entityModel, null);
        sb.append('\n');
        sb.where();
        MutableIntArray params = new MutableIntArray(entityModel.getPkColumns().size() + 2);
        genEntityFilter(params, sb, dialect, null, entityModel, binders);

        if (entityModel.getVersionPropId() > 0) {
            sb.and();
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getVersionPropId(), false);
            params.add(col.getPropId());
            appendEq(sb, dialect, null, col, binders[col.getPropId()], null);
        }
        return new EntitySQL(sb.end(), ImmutableIntArray.EMPTY, params);
    }

    public static EntitySQL genLockSql(IDialect dialect, IEntityModel entityModel, IDataParameterBinder[] binders,
                                       IntArray propIds, LockOption lockOption) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("lock:" + entityModel.getName());
        sb.querySpace(entityModel.getQuerySpace());

        sb.select();
        genSelectFields(sb, dialect, null, entityModel, propIds);
        sb.append('\n');
        sb.from();
        table(sb, dialect, entityModel, null);
        sb.append(dialect.getLockHintSql(lockOption));
        sb.append('\n');
        sb.where();
        MutableIntArray params = new MutableIntArray(entityModel.getPkColumns().size() + 2);
        genEntityFilter(params, sb, dialect, null, entityModel, binders);
        sb.append(' ');
        sb.append(dialect.getForUpdateSql(lockOption));
        return new EntitySQL(sb.end(), propIds, params);
    }

    public static EntitySQL genFindLatestSql(IDialect dialect, IEntityModel entityModel, IDataParameterBinder[] binders,
                                             IntArray propIds) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("findLatest:" + entityModel.getName());
        sb.querySpace(entityModel.getQuerySpace());
        sb.select();
        genSelectFields(sb, dialect, null, entityModel, propIds);
        sb.append('\n');
        sb.from();
        table(sb, dialect, entityModel, null);
        sb.append('\n');
        sb.where();
        IColumnModel revCol = entityModel.getColumnByPropId(entityModel.getNopRevEndVarPropId(), false);
        appendCol(sb, dialect, null, revCol);
        sb.append('=').append(OrmConstants.NOP_VER_MAX_VALUE);

        addFixedFilter(false, sb, dialect, null, entityModel, binders);

        MutableIntArray params = new MutableIntArray(entityModel.getPkColumns().size() + 1);

        for (IColumnModel col : entityModel.getColumns()) {
            if (col.getPropId() != entityModel.getNopRevBeginVerPropId()) {
                sb.and();
                params.add(col.getPropId());
                appendEq(sb, dialect, null, col, binders[col.getPropId()], null);
            }
        }
        if (entityModel.isUseTenant()) {
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getTenantPropId(), false);
            if (!col.isPrimary()) {
                sb.and();
                params.add(col.getPropId());
                appendEq(sb, dialect, null, col, binders[col.getPropId()], null);
            }
        }
        return new EntitySQL(sb.end(), propIds, params);
    }

    public static EntitySQL genInsertSql(IDialect dialect, IEntityModel entityModel, IDataParameterBinder[] binders) {
        SQL.SqlBuilder sb = new SQL.SqlBuilder();
        sb.name("insert:" + entityModel.getName());
        sb.querySpace(entityModel.getQuerySpace());

        sb.append(dialect.getInsertKeyword()).append(" into ");
        table(sb, dialect, entityModel, null);
        sb.append("(\n");

        int colCount = 0;
        for (IColumnModel col : entityModel.getColumns()) {
            if (col.isInsertable()) {
                appendCol(sb, dialect, null, col);
                if (colCount % 5 == 4)
                    sb.append('\n');
                colCount++;
                sb.append(',');
            }
        }

        // 删除最后一个逗号
        sb.deleteTail(1);
        sb.append("\n) values (\n");

        MutableIntArray params = new MutableIntArray(entityModel.getColumns().size());
        colCount = 0;
        for (IColumnModel col : entityModel.getColumns()) {
            if (col.isInsertable()) {
                params.add(col.getPropId());
                sb.typeParam(binders[col.getPropId()], null, col.containsTag(OrmConstants.TAG_MASKED));
                if (colCount % 5 == 4)
                    sb.append('\n');
                colCount++;
                sb.append(',');
            }
        }
        sb.deleteTail(1);
        sb.append("\n)");
        return new EntitySQL(sb.end(), ImmutableIntArray.EMPTY, params);
    }

    public static EntitySQL genUpdateSql(IDialect dialect, IEntityModel entityModel, IDataParameterBinder[] binders,
                                         IntArray propIds) {
        SQL.SqlBuilder sb = new SQL.SqlBuilder();
        sb.name("update:" + entityModel.getName());
        sb.querySpace(entityModel.getQuerySpace());

        sb.append(dialect.getUpdateKeyword()).append(" ");
        table(sb, dialect, entityModel, null);
        sb.set();

        if (entityModel.getVersionPropId() > 0) {
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getVersionPropId(), false);
            appendCol(sb, dialect, null, col);
            sb.append("=");
            appendCol(sb, dialect, null, col);
            sb.append(" +1,");
        }

        MutableIntArray params = new MutableIntArray(propIds.size() + 1);

        for (int i = 0, n = propIds.size(); i < n; i++) {
            int propId = propIds.get(i);
            IColumnModel col = entityModel.getColumnByPropId(propId, false);
            if (!col.isUpdatable())
                throw new OrmException(ERR_ORM_ENTITY_PROP_NOT_UPDATABLE).param(ARG_ENTITY_NAME, entityModel.getName())
                        .param(ARG_PROP_NAME, col.getName());

            params.add(propId);
            appendEq(sb, dialect, null, col, binders[propId], null);
            sb.append(',');
        }

        // 删除最后一个逗号
        sb.deleteTail(1);
        sb.where();
        genEntityFilter(params, sb, dialect, null, entityModel, binders);

        if (entityModel.getVersionPropId() > 0) {
            sb.and();
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getVersionPropId(), false);
            params.add(col.getPropId());
            appendEq(sb, dialect, null, col, binders[col.getPropId()], null);
        }
        return new EntitySQL(sb.end(), propIds, params);
    }

    public static CollectionSQL genCollectionLoadSql(IDialect dialect, IEntityRelationModel collectionModel,
                                                     IDataParameterBinder[] binders, IntArray propIds) {
        IEntityModel refEntityModel = collectionModel.getRefEntityModel();
        // 首先确保propIds中包含owner属性
        if (propIds != refEntityModel.getAllPropIds())
            propIds = propIds.merge(collectionModel.getRefPropIds());

        IEntityModel entityModel = collectionModel.getRefEntityModel();

        SQL.SqlBuilder sb = SQL.begin();
        sb.name("load_collection:" + collectionModel.getCollectionName());
        sb.querySpace(collectionModel.getRefEntityModel().getQuerySpace());

        sb.select();
        genSelectFields(sb, dialect, null, entityModel, propIds);
        sb.append('\n');
        sb.from();
        table(sb, dialect, entityModel, null);
        sb.append('\n');
        sb.where();
        sb.alwaysTrue();

        genCollectionFilterEx(sb, dialect, entityModel, null, binders);

        List<? extends IEntityJoinConditionModel> joins = collectionModel.getJoin();
        MutableIntArray paramPropIds = new MutableIntArray(joins.size());

        for (IEntityJoinConditionModel join : joins) {
            IColumnModel rightCol = (IColumnModel) join.getRightPropModel();

            if (rightCol != null) {
                sb.and();
                appendCol(sb, dialect, null, rightCol);
                sb.append('=');
                if (join.getLeftPropModel() != null) {
                    sb.typeParam(binders[join.getRightPropModel().getColumnPropId()], null,
                            rightCol.containsTag(OrmConstants.TAG_MASKED));
                    paramPropIds.add(join.getLeftPropModel().getColumnPropId());
                } else {
                    sb.append(dialect.getValueLiteral(join.getLeftValue()));
                }
            }
        }

        genOrderBy(sb, dialect, refEntityModel, null, collectionModel.getSort());

        return new CollectionSQL(sb.end(), propIds, paramPropIds);
    }

    public static CollectionSQL genCollectionBatchLoadSqlPart(IDialect dialect, IEntityRelationModel collectionModel,
                                                              IntArray propIds, IDataParameterBinder[] binders) {
        IEntityModel refEntityModel = collectionModel.getRefEntityModel();
        // 首先确保propIds中包含owner属性
        if (propIds != refEntityModel.getAllPropIds())
            propIds = propIds.merge(collectionModel.getRefPropIds());

        IEntityModel entityModel = collectionModel.getRefEntityModel();

        SQL.SqlBuilder sb = SQL.begin();
        sb.name("batch_load_collection:" + collectionModel.getCollectionName());
        sb.querySpace(entityModel.getQuerySpace());

        sb.select();
        genSelectFields(sb, dialect, null, entityModel, propIds);
        sb.from();
        table(sb, dialect, entityModel, null);
        sb.where();
        sb.alwaysTrue();

        genCollectionFilterEx(sb, dialect, entityModel, null, binders);

        List<? extends IEntityJoinConditionModel> joins = collectionModel.getJoin();
        for (IEntityJoinConditionModel join : joins) {
            IEntityPropModel leftCol = join.getLeftPropModel();
            IColumnModel rightCol = (IColumnModel) join.getRightPropModel();

            if (rightCol != null && leftCol == null) {
                sb.and();
                appendCol(sb, dialect, null, rightCol);
                sb.append('=');
                Object value = join.getLeftValue();
                sb.append(dialect.getValueLiteral(value));
            }
        }

        sb.and();

        List<? extends IEntityJoinConditionModel> ownerJoins = getOwnerBatchLoadJoins(collectionModel);
        if (ownerJoins.size() == 1) {
            IColumnModel col = (IColumnModel) ownerJoins.get(0).getRightPropModel();
            appendCol(sb, dialect, null, col);
        } else {
            sb.append('(');
            for (int i = 0, n = ownerJoins.size(); i < n; i++) {
                IEntityJoinConditionModel ownerJoin = ownerJoins.get(i);
                IColumnModel col = (IColumnModel) ownerJoin.getRightPropModel();
                if (i != 0)
                    sb.append(',');
                appendCol(sb, dialect, null, col);
            }
            sb.append(')');
        }
        sb.append(" in ");
        return new CollectionSQL(sb.end(), propIds, ImmutableIntArray.EMPTY);
    }

    public static boolean genCollectionFilterEx(SQL.SqlBuilder sb, IDialect dialect, IEntityModel entityModel,
                                                String owner, IDataParameterBinder[] binders) {
        boolean append = false;

        if (entityModel.hasFilter()) {
            entityModel.getFilters().forEach(filter -> {
                sb.and();
                OrmColumnModel col = filter.getColumn();
                appendEq(sb, dialect, owner, col, binders[col.getPropId()], filter.getValue());
            });
        }

        if (entityModel.isUseRevision()) {
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getNopRevEndVarPropId(), false);
            sb.and();
            appendCol(sb, dialect, owner, col);
            sb.append('=');
            sb.append(OrmConstants.NOP_VER_MAX_VALUE);
            append = true;
        }

        if (entityModel.isUseTenant()) {
            IColumnModel tenantCol = entityModel.getColumnByPropId(entityModel.getTenantPropId(), false);
            sb.and();
            appendCol(sb, dialect, owner, tenantCol);
            sb.append('=');
            sb.markWithProvider("?", OrmEqlConstants.MARKER_TENANT_ID, () -> ContextProvider.currentTenantId(), false);
            append = true;
        }

        if (entityModel.isUseLogicalDelete()) {
            int startPos = sb.length();
            IColumnModel deleteFlagCol = entityModel.getColumnByPropId(entityModel.getDeleteFlagPropId(), false);
            sb.and();
            appendCol(sb, dialect, owner, deleteFlagCol);
            String deleted = getBooleanLiteral(deleteFlagCol.getStdSqlType(), dialect, false);
            sb.append("= ").append(deleted);
            append = true;
            int endPos = sb.length();
            sb.appendMarker(new Markers.NameMarker(startPos, endPos, "logicalDelete"));
        }
        return append;
    }

    public static String getBooleanLiteral(StdSqlType sqlType, IDialect dialect, boolean value) {
        if (sqlType == StdSqlType.BOOLEAN) {
            return dialect.getBooleanValueLiteral(value);
        }
        if (sqlType == StdSqlType.VARCHAR)
            return dialect.getStringLiteral(value ? "1" : "0");
        return value ? "1" : "0";
    }

    static List<IEntityJoinConditionModel> getOwnerBatchLoadJoins(IEntityRelationModel ref) {
        IEntityJoinConditionModel singleJoin = ref.getSingleColumnJoin();
        if (singleJoin != null)
            return Collections.singletonList(singleJoin);

        List<IEntityJoinConditionModel> joins = new ArrayList<>(ref.getJoin().size());
        for (IEntityJoinConditionModel join : ref.getJoin()) {
            if (join.getLeftProp() == null)
                continue;

            if (join.getRightPropModel().getColumnPropId() == ref.getRefEntityModel().getTenantPropId())
                continue;

            joins.add(join);
        }
        return joins;
    }

    public static void appendBatchCollectionIn(SQL.SqlBuilder sb, IDialect dialect,
                                               IEntityRelationModel collectionModel, IDataParameterBinder[] binders,
                                               Collection<IOrmEntitySet> collections) {
        List<? extends IEntityJoinConditionModel> ownerJoins = getOwnerBatchLoadJoins(collectionModel);
        if (ownerJoins.size() == 1) {
            IEntityJoinConditionModel ownerJoin = ownerJoins.get(0);
            sb.append('(');
            int i = 0;
            for (IOrmEntitySet coll : collections) {
                if (i != 0)
                    sb.append(',');
                IOrmEntity entity = coll.orm_owner();
                sb.param(cast(entity, ownerJoin.getLeftProp(), OrmEntityHelper.getLeftValue(ownerJoin, entity),
                        ownerJoin.getRightType()));
                i++;
            }
            sb.append(')');
        } else {
            sb.append('(');
            int i = 0;
            for (IOrmEntitySet coll : collections) {
                if (i != 0)
                    sb.append(',');
                IOrmEntity entity = coll.orm_owner();
                sb.append('(');
                for (int k = 0, m = ownerJoins.size(); k < m; k++) {
                    if (k != 0)
                        sb.append(',');
                    IEntityJoinConditionModel ownerJoin = ownerJoins.get(k);
                    sb.param(cast(entity, ownerJoin.getLeftProp(), OrmEntityHelper.getLeftValue(ownerJoin, entity),
                            ownerJoin.getRightType()));
                }
                sb.append(')');
                i++;
            }
            sb.append(')');
        }
    }

    public static void genOrderBy(SQL.SqlBuilder sb, IDialect dialect, IEntityModel entityModel, String owner,
                                  List<OrderFieldBean> orderBy) {
        if (orderBy == null || orderBy.isEmpty())
            return;
        sb.append('\n');
        sb.orderBy();

        for (int i = 0, n = orderBy.size(); i < n; i++) {
            if (i != 0)
                sb.append(',');
            OrderFieldBean orderField = orderBy.get(i);
            IColumnModel col = entityModel.getColumn(orderField.getName(), false);
            appendCol(sb, dialect, owner, col);
            sb.desc(orderField.isDesc());
            sb.nullsFirst(orderField.getNullsFirst());
        }
    }

    public static SQL.SqlBuilder transform(IDialect dialect, ShardSelection shard, SQL sql, IOrmEntity entity,
                                           IntArray paramPropIds) {
        MutableInt index = new MutableInt();
        SQL.SqlBuilder sb = SQL.begin();
        sb.name(sql.getName()).append(sql);
        if (entity != null)
            sb.querySpace(entity.orm_entityModel().getQuerySpace());

        sb.changeMarker(marker -> {
            if (marker instanceof TypedValueMarker) {
                int propId = paramPropIds.get(index.getAndIncrement());
                Object value = entity.orm_propValue(propId);
                TypedValueMarker typedMarker = (TypedValueMarker) marker;
                // 数据库中的数据类型可能与java属性的类型不同，需要进行类型转换
                StdDataType sqlType = typedMarker.getStdDataType();
                value = sqlType.convert(value,
                        err -> OrmException.newError(err, entity).param(ARG_PROP_NAME, entity.orm_propName(propId)));
                return typedMarker.changeValue(value);
            } else if (marker instanceof Markers.ProviderMarker) {
                return ((Markers.ProviderMarker) marker).buildValueMarker();
                // } else if (marker instanceof Markers.ValueMarker) {
                // int propId = paramPropIds.get(index.getAndIncrement());
                // Object value = entity.orm_propValue(propId);
                // return ((Markers.ValueMarker) marker).changeValue(value);
            } else {
                return marker;
            }
        });

        transformShard(sb, dialect, shard);

        return sb;
    }

    public static void transformShard(SQL.SqlBuilder sb, IDialect dialect, ShardSelection shard) {
        if (shard != null && shard.getQuerySpace() != null) {
            sb.querySpace(shard.getQuerySpace());
        }
        if (shard != null && shard.getShardName() != null) {
            sb.transformMarker(marker -> {
                if (marker instanceof SyntaxMarker) {
                    SyntaxMarker syntaxMarker = (SyntaxMarker) marker;
                    if (syntaxMarker.getType() == SyntaxMarker.SyntaxMarkerType.TABLE) {
                        // {原表名}_{shardName}
                        String tableName = syntaxMarker.getMarkedText(sb.getTextSequence());
                        tableName = dialect.unescapeSQLName(tableName);
                        tableName = dialect.escapeSQLName(tableName + '_' + shard.getShardName());
                        return new MarkedString(tableName);
                    }
                }
                return null;
            });
        }
    }

    public static void genEntityFilter(MutableIntArray params, SQL.SqlBuilder sb, IDialect dialect, String owner,
                                       IEntityModel entityModel, IDataParameterBinder[] binders) {
        genIdEq(params, sb, dialect, owner, entityModel, binders);
        addFixedFilter(true, sb, dialect, owner, entityModel, binders);
        genEntityTenantFilter(params, sb, dialect, owner, entityModel, binders);
    }

    private static void addFixedFilter(boolean prependAnd, SQL.SqlBuilder sb, IDialect dialect, String owner,
                                       IEntityModel entityModel, IDataParameterBinder[] binders) {
        if (entityModel.hasFilter()) {
            entityModel.getFilters().forEach(filter -> {
                // 如果是主键的一部分，则不用增加额外的过滤条件
                if (filter.getColumn().isPrimary())
                    return;

                if (prependAnd)
                    sb.and();
                OrmColumnModel col = filter.getColumn();
                appendEq(sb, dialect, owner, col, null, filter.getValue());
                if (!prependAnd)
                    sb.and();
            });
        }
    }

    public static void genEntityTenantFilter(MutableIntArray params, SQL.SqlBuilder sb, IDialect dialect, String owner,
                                             IEntityModel entityModel, IDataParameterBinder[] binders) {
        if (entityModel.isUseTenant()) {
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getTenantPropId(), false);
            if (!col.isPrimary()) {
                sb.and();
                params.add(col.getPropId());
                appendEq(sb, dialect, owner, col, binders[col.getPropId()], null);
            }
        }
    }

    public static void table(SQL.SqlBuilder sb, IDialect dialect, IEntityModel entityModel, String owner) {
        sb.markTable(dialect.escapeSQLName(entityModel.getTableName()), owner, entityModel.getName(), dialect.isUseAsInFrom());
    }

    public static void genIdEq(MutableIntArray params, SQL.SqlBuilder sb, IDialect dialect, String owner,
                               IEntityModel entityModel, IDataParameterBinder[] binders) {
        List<? extends IColumnModel> cols = entityModel.getPkColumns();
        for (int i = 0, n = cols.size(); i < n; i++) {
            IColumnModel col = cols.get(i);
            if (i != 0)
                sb.and();

            params.add(col.getPropId());
            appendEq(sb, dialect, owner, col, binders[col.getPropId()], null);
        }
    }

    public static void genSelectFields(SQL.SqlBuilder sb, IDialect dialect, String owner, IEntityModel entityModel,
                                       IntArray propIds) {
        for (int i = 0, n = propIds.size(); i < n; i++) {
            if (i != 0) {
                sb.append(',');
            }
            IColumnModel col = entityModel.getColumnByPropId(propIds.get(i), false);
            appendCol(sb, dialect, owner, col);
        }
    }

    public static void appendEq(SQL.SqlBuilder sb, IDialect dialect, String owner, IColumnModel col,
                                IDataParameterBinder binder, Object value) {
        appendCol(sb, dialect, owner, col);
        sb.append("=");
        sb.typeParam(binder, value, col.containsTag(OrmConstants.TAG_MASKED)).append(' ');
    }

    public static Object cast(IOrmEntity entity, IColumnModel col, Object value) {
        return cast(entity, col.getName(), value, col.getStdDataType());
    }

    public static Object castProp(IOrmEntity entity, IEntityPropModel propModel, StdDataType targetType) {
        Object value = OrmEntityHelper.getPropValue(propModel, entity);
        return cast(entity, propModel.getName(), value, targetType);
    }

    public static Object cast(IOrmEntity entity, String propName, Object value, StdDataType targetType) {
        return ConvertHelper.convertTo(targetType.getJavaClass(), value,
                err -> new NopException(err).param(ARG_PROP_NAME, propName)
                        .param(ARG_ENTITY_NAME, entity.orm_entityName()).param(ARG_ENTITY_ID, entity.get_id()));
    }

    public static SQL.SqlBuilder genCountByExample(IDialect dialect, IEntityModel entityModel,
                                                   IDataParameterBinder[] binders, IOrmEntity example) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("countByExample:" + entityModel.getName());
        sb.querySpace(entityModel.getQuerySpace());

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
        sb.querySpace(entityModel.getQuerySpace());

        sb.deleteFrom();
        table(sb, dialect, entityModel, null);
        sb.where().alwaysTrue();
        appendExampleFilter(sb, dialect, owner, entityModel, binders, example);
        return sb;
    }

    public static SQL.SqlBuilder genFindByExample(IDialect dialect, IEntityModel entityModel,
                                                  IDataParameterBinder[] binders, IOrmEntity example, List<OrderFieldBean> orderBy) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("findByExample:" + entityModel.getName());
        sb.querySpace(entityModel.getQuerySpace());

        sb.select();
        genSelectFields(sb, dialect, "o", entityModel, entityModel.getEagerLoadProps());
        sb.from();
        table(sb, dialect, entityModel, "o");

        sb.where().alwaysTrue();
        appendExampleFilter(sb, dialect, "o", entityModel, binders, example);
        appendOrderBy(sb, dialect, "o", entityModel, orderBy);
        return sb;
    }

    public static SQL.SqlBuilder genUpdateByExample(IDialect dialect, IEntityModel entityModel,
                                                    IDataParameterBinder[] binders, IOrmEntity example, IOrmEntity updated) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.name("updateByExample:" + entityModel.getName());
        sb.querySpace(entityModel.getQuerySpace());

        sb.append(dialect.getUpdateKeyword());
        table(sb, dialect, entityModel, "o");
        sb.append('\n');
        sb.set();
        if (entityModel.getVersionPropId() > 0) {
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getVersionPropId(), false);
            appendCol(sb, dialect, null, col);
            sb.append("=");
            appendCol(sb, dialect, null, col);
            sb.append(" +1,");
        }

        updated.orm_forEachInitedProp((value, propId) -> {
            IColumnModel col = entityModel.getColumnByPropId(propId, false);
            if (!col.isUpdatable())
                return;
            appendEq(sb, dialect, null, col, binders[propId], value);
            sb.append(',');
        });

        sb.deleteTail(1);
        sb.append('\n');
        sb.where().alwaysTrue();
        appendExampleFilter(sb, dialect, "o", entityModel, binders, example);
        return sb;
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

    public static void appendOrderBy(SQL.SqlBuilder sb, IDialect dialect, String owner, IEntityModel entityModel,
                                     List<OrderFieldBean> orderBy) {
        if (orderBy == null || orderBy.isEmpty())
            return;

        sb.append('\n').orderBy();
        for (int i = 0, n = orderBy.size(); i < n; i++) {
            OrderFieldBean orderField = orderBy.get(i);
            if (i != 0)
                sb.append(',');

            IColumnModel col = entityModel.getColumn(orderField.getName(), false);
            sb.owner(owner);
            sb.append(dialect.escapeSQLName(col.getCode()));
            sb.desc(orderField.isDesc());
            sb.nullsFirst(orderField.getNullsFirst());
        }
    }

}
