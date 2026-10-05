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
import io.nop.core.lang.sql.TypedParamMarker;
import io.nop.core.lang.sql.TypedValueMarker;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.lock.LockOption;
import io.nop.dao.shard.ShardSelection;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmConstants;
import io.nop.orm.enc.EncAssistedHelper;
import io.nop.orm.eql.OrmEqlConstants;
import io.nop.orm.eql.sql.NullsOrderRewriter;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.support.OrmEntityHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_PROP_NOT_UPDATABLE;
import static io.nop.orm.OrmErrors.ARG_DIALECT;
import static io.nop.orm.OrmErrors.ARG_REASON;
import static io.nop.orm.OrmErrors.ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE;
import static io.nop.orm.OrmErrors.ERR_ORM_NULLS_ORDER_NOT_SUPPORTED;
import static io.nop.orm.eql.utils.EqlHelper.appendCol;
import static io.nop.orm.eql.utils.EqlHelper.replaceOwner;

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
            // plan 2318 判定：租户列是 useTenant 自动维护的系统列，不可能声明 enc+辅助列，不改写
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
            // plan 2318 判定：version 是框架系统列，不可能声明 enc+辅助列，不改写
            sb.and();
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getVersionPropId(), false);
            params.add(col.getPropId());
            appendEqMarker(sb, dialect, null, col, binders[col.getPropId()]);
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
            if (col.getPropId() == entityModel.getNopRevBeginVerPropId())
                continue;
            // revEnd列已在WHERE中以NOP_VER_MAX_VALUE字面量出现，绑定实体值会导致条件恒不匹配
            if (col.getPropId() == entityModel.getNopRevEndVarPropId())
                continue;
            {
                // plan 2318 判定：rev 机制的全列状态比对（值执行期从实体绑定，非构建期已知过滤），
                // enc 主列在随机 IV 下密文比对恒不命中、辅助列也无法表达全行比对——声明了辅助列的
                // enc 主列显式失败（同 Phase 3 错误码矩阵），未声明辅助列的 enc 列维持既有 limitation 行为
                checkEncNotSearchable(entityModel, col, "rev-find-latest-all-columns-not-searchable");
                sb.and();
                params.add(col.getPropId());
                appendEqMarker(sb, dialect, null, col, binders[col.getPropId()]);
            }
        }
        if (entityModel.isUseTenant()) {
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getTenantPropId(), false);
            if (!col.isPrimary()) {
                // plan 2318 判定：租户列是系统列，不可能声明 enc+辅助列，不改写
                sb.and();
                params.add(col.getPropId());
                appendEqMarker(sb, dialect, null, col, binders[col.getPropId()]);
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
                // plan 2318 判定：INSERT VALUES 是写路径，enc 列加密由增强 binder 完成（非检索过滤），不改写
                sb.typeParamMarker(binders[col.getPropId()], col.containsTag(OrmConstants.TAG_MASKED));
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
            // plan 2318 判定：单实体 UPDATE 的 SET 侧是写路径（辅助列派生在 flush 链路 Phase 2 完成），不改写
            appendEqMarker(sb, dialect, null, col, binders[propId]);
            sb.append(',');
        }

        // 删除最后一个逗号
        sb.deleteTail(1);
        sb.where();
        genEntityFilter(params, sb, dialect, null, entityModel, binders);

        if (entityModel.getVersionPropId() > 0) {
            // plan 2318 判定：version 是框架系统列，不可能声明 enc+辅助列，不改写
            sb.and();
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getVersionPropId(), false);
            params.add(col.getPropId());
            appendEqMarker(sb, dialect, null, col, binders[col.getPropId()]);
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
                    // plan 2318 判定：集合关联的 join 列是 FK 关联列（执行期取 owner 值），非 enc 主列检索场景，不改写
                    sb.typeParamMarker(binders[join.getRightPropModel().getColumnPropId()],
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
                // plan 2318 Phase 4：固定 filter 等值在 enc 主列上改写到 assisted 列（值构建期已知）
                appendFilterEq(sb, dialect, owner, entityModel, col, binders, filter.getValue());
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


    /**
     * 渲染排序列的 SQL 文本（复刻 {@code EqlHelper.appendCol} 的两条分支：
     * 计算列走 {@code sqlText}，普通列走 {@code owner.code}）。
     *
     * <p>改写需要把排序列写两遍，故必须先拿到文本；{@code appendCol} 只往 builder 里写，
     * 不返回文本，故此处单独渲染。
     */
    public static String renderOrderByColumn(IDialect dialect, String owner, IColumnModel col) {
        String sqlText = col.getSqlText();
        if (sqlText != null)
            return replaceOwner(owner, sqlText);
        StringBuilder sb = new StringBuilder();
        if (owner != null)
            sb.append(owner).append('.');
        return sb.append(dialect.normalizeColumnName(col.getCode())).toString();
    }

    /**
     * Plan 2309 D1/D7：bean 通道的 ORDER BY 空序门控。
     *
     * <p>与 EQL 通道共用 {@link NullsOrderRewriter} 的改写实现（plan 2309 Phase 1），
     * 全仓只有一份改写逻辑。调用方只负责按 {@code supportNullsFirst} 二选一：
     * true → 原生直出；false → CASE 改写。
     *
     * <p>不做「请求等于方言缺省则省略子句」的优化（design §3.8）。
     *
     * @param columnSql 已渲染好的排序列 SQL（含 owner 前缀），改写时会被写两遍
     * @param desc      是否降序；内部按 {@code asc = !desc} 传给改写单元
     */
    public static void appendNullsOrder(SQL.SqlBuilder sb, IDialect dialect, String columnSql,
                                        boolean desc, Boolean nullsFirst) {
        boolean asc = !desc;
        if (nullsFirst == null || dialect.isSupportNullsFirst()) {
            NullsOrderRewriter.appendNative(sb, out -> out.append(columnSql), asc, nullsFirst);
            return;
        }
        Optional<String> blocker = NullsOrderRewriter.duplicationBlocker(columnSql);
        if (blocker.isPresent()) {
            // 不可复制就不产出改写后的 SQL——宁可响亮失败，也不给出语义可能错乱的语句
            throw new NopException(ERR_ORM_NULLS_ORDER_NOT_SUPPORTED)
                    .param(ARG_REASON, blocker.get())
                    .param(ARG_DIALECT, dialect.getName());
        }
        NullsOrderRewriter.appendCaseRewrite(sb, out -> out.append(columnSql), asc, nullsFirst);
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
            // getColumn(name,false)对未知列名会抛出ERR_ORM_UNKNOWN_COLUMN，无需额外判空
            IColumnModel col = entityModel.getColumn(orderField.getName(), false);
            // plan 2318：ORDER BY 触及声明了辅助列的 enc 主列时按密文排序（静默错误结果），与 EQL 路径
            // 同一错误码矩阵显式失败；未声明辅助列的 enc 列维持现状
            checkEncNotSearchable(entityModel, col, "order-by-or-group-by-not-searchable");
            appendNullsOrder(sb, dialect, renderOrderByColumn(dialect, owner, col),
                    orderField.isDesc(), orderField.getNullsFirst());
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
            if (marker instanceof TypedParamMarker) {
                int propId = paramPropIds.get(index.getAndIncrement());
                Object value = entity.orm_propValue(propId);
                TypedParamMarker typedMarker = (TypedParamMarker) marker;
                // 数据库中的数据类型可能与java属性的类型不同，需要进行类型转换
                StdDataType sqlType = typedMarker.getStdDataType();
                value = sqlType.convert(value,
                        err -> OrmException.newError(err, entity).param(ARG_PROP_NAME, entity.orm_propName(propId)));
                return typedMarker.changeValue(value);
            } else if (marker instanceof Markers.ParamMarker) {
                int propId = paramPropIds.get(index.getAndIncrement());
                Object value = entity.orm_propValue(propId);
                return ((Markers.ValueMarker) marker).changeValue(value);
            } else if (marker instanceof Markers.ProviderMarker) {
                return ((Markers.ProviderMarker) marker).buildValueMarker();
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
                        tableName = dialect.normalizeTableName(tableName + '_' + shard.getShardName());
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
                // plan 2318 Phase 4：固定 filter 等值在 enc 主列上改写到 assisted 列（值构建期已知）
                appendFilterEq(sb, dialect, owner, entityModel, col, binders, filter.getValue());
                if (!prependAnd)
                    sb.and();
            });
        }
    }

    public static void genEntityTenantFilter(MutableIntArray params, SQL.SqlBuilder sb, IDialect dialect, String owner,
                                             IEntityModel entityModel, IDataParameterBinder[] binders) {
        if (entityModel.isUseTenant()) {
            // plan 2318 判定：租户列是 useTenant 自动维护的系统列，不可能声明 enc+辅助列，不改写
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getTenantPropId(), false);
            if (!col.isPrimary()) {
                sb.and();
                params.add(col.getPropId());
                appendEqMarker(sb, dialect, owner, col, binders[col.getPropId()]);
            }
        }
    }

    public static void table(SQL.SqlBuilder sb, IDialect dialect, IEntityModel entityModel, String owner) {
        sb.markTable(dialect.normalizeTableName(entityModel.getTableName()), owner, entityModel.getName(), dialect.isUseAsInFrom());
    }

    public static void genIdEq(MutableIntArray params, SQL.SqlBuilder sb, IDialect dialect, String owner,
                               IEntityModel entityModel, IDataParameterBinder[] binders) {
        List<? extends IColumnModel> cols = entityModel.getPkColumns();
        for (int i = 0, n = cols.size(); i < n; i++) {
            IColumnModel col = cols.get(i);
            // plan 2318 判定：按主键装载/锁定/删除/更新的 WHERE 是持久化机制而非检索过滤（值执行期绑定），
            // enc 主列在随机 IV 下密文比对恒不命中、辅助列摘要无法定位物理行——声明了辅助列的 enc 主键列
            // 显式失败（同 Phase 3 错误码矩阵）；未声明辅助列的 enc 主键列维持既有 limitation 行为
            checkEncNotSearchable(entityModel, col, "primary-key-not-searchable");
            if (i != 0)
                sb.and();

            params.add(col.getPropId());
            appendEqMarker(sb, dialect, owner, col, binders[col.getPropId()]);
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

    /**
     * plan 2318 Phase 4：GenSqlHelper 路径（example 逐列 eq / 实体固定 filter）的等值改写。
     *
     * <p>该路径的 filter 值在 SQL 构建期已知，enc 主列声明了 assisted 辅助列时直接改列到辅助列，
     * 参数值按契约摘要（{@link EncAssistedHelper#assistedDigest}）后绑定，不经 marker builder；
     * binder 同步换用辅助列自身的 binder（摘要是明文的确定性等价物，不得再经主列的加密 binder）。</p>
     *
     * <p>enc 主列只声明了 like 辅助列时，等值语义不可服务，按 Phase 3 同一错误码矩阵显式失败；
     * 未声明任何辅助列的 enc 列维持现状（随机 IV 密文比较，不报错，见设计文档 §3.5）。</p>
     *
     * <p>16 处 {@code binders[...]} 引用（plan 2318 Baseline #5）中进入本方法的只有
     * {@link #genCollectionFilterEx}（固定 filter）、{@link #addFixedFilter}（load/lock/delete/update
     * 的固定 filter）与 {@link #appendExampleFilter}（example 逐列 eq，含 updateByExample 的 WHERE 侧）
     * 三处；其余引用点逐处判定为不改写：租户列/版本列/revision 列是框架系统列（不可能声明 enc+辅助列）、
     * 主键列与 rev 全列比对按 {@link #checkEncNotSearchable} 显式失败（enc+辅助列声明场景）、
     * INSERT/UPDATE SET 是写路径（加密由增强 binder 完成；bulk UPDATE SET 的辅助列边界见 owner doc）、
     * 集合 join 列是 FK 关联（执行期取 owner 值，非 enc 检索场景）。本路径不产出 LIKE 谓词，
     * 无 like 列改写面。</p>
     */
    static void appendFilterEq(SQL.SqlBuilder sb, IDialect dialect, String owner, IEntityModel entityModel,
                               IColumnModel col, IDataParameterBinder[] binders, Object value) {
        IDataParameterBinder binder = binders[col.getPropId()];
        if (col.containsTag(OrmModelConstants.TAG_ENC)) {
            IColumnModel assisted = resolveEncEqTarget(entityModel, col);
            if (assisted != null) {
                col = assisted;
                binder = binders[col.getPropId()];
                if (value != null)
                    value = EncAssistedHelper.instance().assistedDigest(value.toString());
            }
        }
        appendEq(sb, dialect, owner, col, binder, value);
    }

    /**
     * enc 主列的等值改写目标：声明了 assisted 辅助列 → 返回辅助列；未声明辅助列 → null（维持现状）；
     * 只声明 like 辅助列（等值不可服务）→ {@code ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE}。
     */
    static IColumnModel resolveEncEqTarget(IEntityModel entityModel, IColumnModel col) {
        Object assistedName = col.prop_get(OrmModelConstants.EXT_ENC_ASSISTED);
        if (assistedName != null)
            return entityModel.requireColumn(assistedName.toString());
        if (col.prop_get(OrmModelConstants.EXT_ENC_LIKE) != null)
            throw encNotSearchable(entityModel, col, "eq-needs-enc-assisted-helper");
        return null;
    }

    /**
     * enc 主列（声明了辅助列）出现在本路径不可服务的谓词上下文（ORDER BY、按主键/rev 全列比对）
     * 时的显式失败，与 EQL 路径（Phase 3）同一错误码矩阵。
     */
    static void checkEncNotSearchable(IEntityModel entityModel, IColumnModel col, String reason) {
        if (col.containsTag(OrmModelConstants.TAG_ENC)
                && (col.prop_get(OrmModelConstants.EXT_ENC_ASSISTED) != null
                        || col.prop_get(OrmModelConstants.EXT_ENC_LIKE) != null))
            throw encNotSearchable(entityModel, col, reason);
    }

    static NopException encNotSearchable(IEntityModel entityModel, IColumnModel col, String reason) {
        return new NopException(ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE)
                .param(ARG_ENTITY_NAME, entityModel.getName())
                .param(ARG_PROP_NAME, col.getName())
                .param(ARG_REASON, reason);
    }

    public static void appendEq(SQL.SqlBuilder sb, IDialect dialect, String owner, IColumnModel col,
                                IDataParameterBinder binder, Object value) {
        appendCol(sb, dialect, owner, col);
        sb.append("=");
        sb.typeParam(binder, value, col.containsTag(OrmConstants.TAG_MASKED)).append(' ');
    }

    public static void appendEqMarker(SQL.SqlBuilder sb, IDialect dialect, String owner, IColumnModel col,
                                IDataParameterBinder binder) {
        appendCol(sb, dialect, owner, col);
        sb.append("=");
        sb.typeParamMarker(binder, col.containsTag(OrmConstants.TAG_MASKED)).append(' ');
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

        // update关键字无尾随空格（如H2缺省"update"），必须显式补空格，否则生成"updateTABLE as o"非法SQL
        sb.append(dialect.getUpdateKeyword()).append(' ');
        table(sb, dialect, entityModel, "o");
        sb.append('\n');
        sb.set();

        int[] setItemCount = {0};
        if (entityModel.getVersionPropId() > 0) {
            IColumnModel col = entityModel.getColumnByPropId(entityModel.getVersionPropId(), false);
            appendCol(sb, dialect, null, col);
            sb.append("=");
            appendCol(sb, dialect, null, col);
            sb.append(" +1,");
            setItemCount[0]++;
        }

        updated.orm_forEachInitedProp((value, propId) -> {
            IColumnModel col = entityModel.getColumnByPropId(propId, false);
            if (!col.isUpdatable())
                return;
            // plan 2318 判定：updateByExample 的 SET 侧是 bulk UPDATE 写路径——辅助列不维护是既定用户裁定
            // （归业务层校验，见 owner doc 限制清单），写路径加密由增强 binder 完成，不改写不报错。
            // SET 值在构建期已知（updated 实体），必须用带值绑定：此前误用 appendEqMarker（无值 marker），
            // 而 driver 直接 end() 执行本 builder、不经 transform 填充，导致 SET 参数永远未绑定（live defect）
            appendEq(sb, dialect, null, col, binders[propId], value);
            sb.append(',');
            setItemCount[0]++;
        });

        // 没有任何可更新字段时SET段为空，deleteTail会产生非法SQL，此处显式报错
        if (setItemCount[0] == 0) {
            throw new OrmException(ERR_ORM_ENTITY_PROP_NOT_UPDATABLE).param(ARG_ENTITY_NAME, entityModel.getName());
        }
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
            // plan 2318 Phase 4：example 逐列 eq（含 updateByExample 的 WHERE 侧）在 enc 主列上
            // 改写到 assisted 列；其余逐列 eq（examId 等非 enc 列）经 resolveEncEqTarget 返回 null 零影响
            appendFilterEq(sb, dialect, owner, entityModel, col, binders, value);
        });

        // 租户列是 useTenant 自动维护的系统列，不可能声明 enc+辅助列，不改写（plan 2318 16 处清单判定）
        if (entityModel.isUseTenant() && entityModel.getTenantPropId() > 0) {
            int tenantPropId = entityModel.getTenantPropId();
            // example已携带租户条件时不重复追加：两个租户值不同时AND条件恒为空，查询静默返回0行
            if (!example.orm_propInited(tenantPropId)) {
                sb.and();
                IColumnModel col = entityModel.getColumnByPropId(tenantPropId, false);
                appendEq(sb, dialect, owner, col, binders[col.getPropId()], ContextProvider.currentTenantId());
            }
        }

        // revision 终止条件是系统列上的常量字面量（NOP_VER_MAX_VALUE），不可能为 enc 场景，不改写
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

            // getColumn(name,false)对未知列名会抛出ERR_ORM_UNKNOWN_COLUMN，无需额外判空
            IColumnModel col = entityModel.getColumn(orderField.getName(), false);
            // plan 2318：example 查询的 ORDER BY 触及声明了辅助列的 enc 主列时按密文排序（静默错误结果），
            // 与 EQL 路径同一错误码矩阵显式失败；未声明辅助列的 enc 列维持现状
            checkEncNotSearchable(entityModel, col, "order-by-or-group-by-not-searchable");
            appendNullsOrder(sb, dialect, renderOrderByColumn(dialect, owner, col),
                    orderField.isDesc(), orderField.getNullsFirst());
        }
    }

}
