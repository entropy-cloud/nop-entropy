/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlQualifiedName;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlWhere;
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

        if (currentSelect == null || outerScope == null) {
            return null;
        }

        SqlTableName tableName = table.getTableName();
        if (tableName == null) {
            return null;
        }

        String rawName = tableName.getName();
        String fullName = tableName.getFullName();

        // 解析 ownerAlias 和属性名 propName
        String ownerAlias;
        String propName;

        SqlQualifiedName owner = tableName.getOwner();
        if (owner != null) {
            // 典型形式：from o.simsClasses c
            ownerAlias = owner.getName();
            propName = rawName;
        } else {
            // 兼容 name="o.simsClasses" 的形式
            int dot = rawName != null ? rawName.indexOf('.') : -1;
            if (dot <= 0) {
                // 如 "SimsClass"、"io.nop.app.SimsClass" 等实体名，直接跳过
                return null;
            }
            ownerAlias = rawName.substring(0, dot);
            propName = rawName.substring(dot + 1);
        }

        SqlTableSource ownerSource = outerScope.getTableByAlias(ownerAlias);
        if (!(ownerSource instanceof SqlSingleTableSource)) {
            return null;
        }
        SqlSingleTableSource ownerTable = (SqlSingleTableSource) ownerSource;

        SqlPropJoin join = buildRelationJoin(visitor, ownerTable, propName);
        if (join == null) {
            return null;
        }

        SqlSingleTableSource right = join.getRight();
        // 保留用户在 from 子句上指定的别名（例如 from o.simsClasses c）
        if (table.getAlias() != null) {
            right.setAlias(table.getAlias());
        }

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
                                                 String propName) {
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
            join = visitor.addToOneRelationJoin(ownerTable, ref);
        } else {
            // 集合关系：只根据 join 条件展开子表，不使用 keyProp 附加过滤
            join = visitor.addToManyCollectionJoin(ownerTable, ref, propName);
        }
        return join;
    }
}
