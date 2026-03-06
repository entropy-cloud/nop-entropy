/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.orm.eql.ast.*;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.eql.meta.ISqlSelectionMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.IOrmDataType;

/**
 * Helper for handling collection-valued properties used as table sources.
 *
 * <p>Example EQL:</p>
 * <pre>
 *   select o.collegeName
 *   from io.nop.app.SimsCollege o
 *   where exists (
 *     select 1
 *     from o.simsClasses c
 *     where c.classId = '11'
 *   )
 * </pre>
 *
 * This helper transforms the collection table source {@code o.simsClasses} into
 * a real entity table (e.g. {@code SimsClass}) and appends the join condition
 * between parent and child tables to the subquery's {@code where} clause.
 */
public final class CollectionTableSourceHelper {

    private CollectionTableSourceHelper() {
    }

    /**
     * Transform a collection-valued property table source (like
     * {@code from o.simsClasses c}) into a real entity table, and append the
     * generated join condition to the current select's {@code where} clause.
     *
     * @param visitor       current {@link EqlTransformVisitor}
     * @param outerScope    outer {@link SqlTableScope} used to resolve owner alias
     * @param currentSelect current {@link SqlQuerySelect}
     * @param table         the {@link SqlSingleTableSource} being visited
     * @return the transformed table source (typically {@link SqlPropJoin#getRight()}),
     *         or {@code null} if this helper does not handle the given table
     */
    public static SqlSingleTableSource transformCollectionTableSource(
            EqlTransformVisitor visitor,
            SqlTableScope outerScope,
            SqlQuerySelect currentSelect,
            SqlSingleTableSource table) {

        SqlTableName tableName = table.getTableName();
        SqlQualifiedName owner = tableName.getOwner();

        SqlTableSource ownerSource = outerScope.getTableByAlias(owner.getName());
        if (!(ownerSource instanceof SqlSingleTableSource)) {
            return null;
        }

        String propName = tableName.getName();

        SqlSingleTableSource ownerTable = (SqlSingleTableSource) ownerSource;

        SqlPropJoin join = buildRelationJoin(visitor, ownerTable, propName, table.getAlias());
        if (join == null) {
            return null;
        }

        SqlSingleTableSource right = join.getRight();


        SqlExpr condition = join.getCondition();
        if (condition != null) {
            SqlWhere where = currentSelect.makeWhere();
            where.appendFilter(condition);
        }

        // 用真实子表替换原始属性表源
        EqlASTNode parent = table.getASTParent();
        if (parent != null) {
            parent.replaceChild(table, right);
        }

        return right;
    }

    /**
     * 基于实体元数据构造属性关联 join（支持 to-one 和 to-many），用于 from owner.prop 作为表来源的场景。
     *
     * 对于 to-many 关系，这里专门使用 {@link EqlTransformVisitor#addToManyCollectionJoin}
     * 来避免原有 resolvePropPath 对 keyProp / propPath 的限制。
     */
    private static SqlPropJoin buildRelationJoin(EqlTransformVisitor visitor,
                                                 SqlSingleTableSource ownerTable,
                                                 String propName, SqlAlias alias) {
        SqlTableName ownerTableName = ownerTable.getTableName();
        ISqlSelectionMeta selMeta = ownerTableName.getResolvedTableMeta();
        if (!(selMeta instanceof ISqlTableMeta)) {
            return null;
        }

        ISqlTableMeta tableMeta = (ISqlTableMeta) selMeta;
        // 允许下划线形式匹配，避免命名风格差异导致解析失败
        ISqlExprMeta fieldExpr = tableMeta.getFieldExprMeta(propName, true);
        if (fieldExpr == null) {
            return null;
        }

        IOrmDataType dataType = fieldExpr.getOrmDataType();
        if (!dataType.getKind().isRelation()) {
            return null;
        }

        IEntityRelationModel ref = (IEntityRelationModel) dataType;
        SqlPropJoin join;
        if (ref.isToOneRelation()) {
            join = visitor.addToOneRelationJoin(ownerTable, ref, alias);
        } else {
            // 集合关系：只根据 join 条件展开子表，不使用 keyProp 附加过滤
            join = visitor.addToManyCollectionJoin(ownerTable, ref, propName, alias);
        }
        return join;
    }
}
