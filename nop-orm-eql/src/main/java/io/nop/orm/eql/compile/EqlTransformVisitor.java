/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.PropPath;
import io.nop.dao.DaoConstants;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.function.ISQLFunction;
import io.nop.orm.eql.OrmEqlConstants;
import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.EqlASTVisitor;
import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.SqlAllProjection;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlCteStatement;
import io.nop.orm.eql.ast.SqlDecorator;
import io.nop.orm.eql.ast.SqlDelete;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlFrom;
import io.nop.orm.eql.ast.SqlInsert;
import io.nop.orm.eql.ast.SqlJoinTableSource;
import io.nop.orm.eql.ast.SqlLiteral;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlOrderBy;
import io.nop.orm.eql.ast.SqlParameterMarker;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlQualifiedName;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlRegularFunction;
import io.nop.orm.eql.ast.SqlSelect;
import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.ast.SqlSubqueryTableSource;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlUpdate;
import io.nop.orm.eql.enums.SqlJoinType;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.eql.meta.ISqlSelectionMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.meta.RenamedSqlExprMeta;
import io.nop.orm.eql.meta.SelectResultTableMeta;
import io.nop.orm.eql.meta.SingleColumnExprMeta;
import io.nop.orm.eql.param.ISqlParamBuilder;
import io.nop.orm.eql.param.TenantParamBuilder;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.utils.EqlASTBuilder;
import io.nop.orm.eql.utils.EqlHelper;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.IOrmDataType;
import io.nop.orm.model.OrmEntityFilterModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.nop.orm.eql.OrmEqlErrors.ARG_ALIAS;
import static io.nop.orm.eql.OrmEqlErrors.ARG_ARG_COUNT;
import static io.nop.orm.eql.OrmEqlErrors.ARG_ARG_INDEX;
import static io.nop.orm.eql.OrmEqlErrors.ARG_COL_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_DECORATOR;
import static io.nop.orm.eql.OrmEqlErrors.ARG_DIALECT;
import static io.nop.orm.eql.OrmEqlErrors.ARG_ENTITY_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_EXPECTED;
import static io.nop.orm.eql.OrmEqlErrors.ARG_EXPECTED_COUNT;
import static io.nop.orm.eql.OrmEqlErrors.ARG_FIELD_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_FUNC_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_LEFT_SOURCE;
import static io.nop.orm.eql.OrmEqlErrors.ARG_MAX_ARG_COUNT;
import static io.nop.orm.eql.OrmEqlErrors.ARG_MIN_ARG_COUNT;
import static io.nop.orm.eql.OrmEqlErrors.ARG_PROP_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_PROP_PATH;
import static io.nop.orm.eql.OrmEqlErrors.ARG_QUERY_SPACE;
import static io.nop.orm.eql.OrmEqlErrors.ARG_QUERY_SPACE_MAP;
import static io.nop.orm.eql.OrmEqlErrors.ARG_TABLE;
import static io.nop.orm.eql.OrmEqlErrors.ARG_TABLE_SOURCE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DECORATOR_ARG_COUNT_IS_NOT_EXPECTED;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DECORATOR_ARG_TYPE_IS_NOT_EXPECTED;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FIELD_NOT_IN_SUBQUERY;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FUNC_TOO_FEW_ARGS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FUNC_TOO_MANY_ARGS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_JOIN_NO_CONDITION;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_JOIN_PROP_PATH_IS_DUPLICATED;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_ALLOW_MULTIPLE_QUERY_SPACE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_MULTI_JOIN_ON_ALIAS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_ONLY_SUPPORT_SINGLE_TABLE_SOURCE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_OWNER_NOT_REF_TO_ENTITY;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_PROP_PATH_JOIN_NOT_ALLOW_CONDITION;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_PROP_PATH_NOT_VALID_TO_ONE_REFERENCE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_QUERY_NO_FROM_CLAUSE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_SELECT_NO_PROJECTIONS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_TABLE_SOURCE_NOT_RESOLVED;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_ALIAS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_COLUMN_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_ENTITY_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_QUERY_SPACE;

public class EqlTransformVisitor extends EqlASTVisitor {

    private final ISqlCompileContext context;
    private IDialect dialect;

    // 预先收集所有明确指定的alias，自动生成的alias需要避免和它们冲突
    private final Set<String> assignedAliases = new HashSet<>();

    private final IAliasGenerator aliasGenerator;

    /**
     * 一条eql语句仅允许在一个querySpace中执行，如果分析发现存在多个querySpace，则抛出异常
     */
    private final Map<String, String> querySpaceToEntityNames = new HashMap<>();
    private String querySpace;

    private SqlTableScope currentScope;

    private List<ISqlParamBuilder> params;

    private final Set<String> readEntityModels = new LinkedHashSet<>();
    private String writeEntityModel;

    /**
     * 判断是否正在处理order by子句。如果仅在order by语句中通过a.b.c这种属性表达式引用关联表上的字段，且关联字段允许为空， 则使用left join来实现隐式关联。
     */
    private boolean inOrderBy;

    public EqlTransformVisitor(ISqlCompileContext context) {
        this.context = context;
        this.aliasGenerator = context.getAliasGenerator();
    }

    public List<String> getReadEntityModels() {
        return new ArrayList<>(readEntityModels);
    }

    public String getWriteEntityModel() {
        return writeEntityModel;
    }

    public IDialect getDialect() {
        return dialect;
    }

    public void transform(SqlProgram program) {
        collectNames(program);
        visitSqlProgram(program);

        SqlParamTypeResolver resolver = new SqlParamTypeResolver(dialect);
        resolver.visit(program);
        this.params = resolver.getParams();
    }

    public List<ISqlParamBuilder> getParams() {
        return params;
    }

    public String getQuerySpace() {
        return querySpace;
    }

    private void collectNames(SqlProgram program) {
        new EqlASTVisitor() {
            int paramIndex = 0;

            @Override
            public void visitSqlAlias(SqlAlias alias) {
                assignedAliases.add(alias.getAlias());
            }

            @Override
            public void visitSqlParameterMarker(SqlParameterMarker param) {
                // 对参数进行编号
                param.setParamIndex(paramIndex++);
            }

        }.visit(program);
    }

    @Override
    public void visitSqlSelectWithCte(SqlSelectWithCte node) {
        if (node.getWithCtes() == null || node.getWithCtes().isEmpty()) {
            visit(node.getSelect());
        } else {
            SqlTableScope scope = new SqlTableScope(node, currentScope);
            currentScope = scope;
            for (SqlCteStatement cteStm : node.getWithCtes()) {
                visit(cteStm.getStatement());
            }
            visit(node.getSelect());
            currentScope = scope.getParent();
        }
    }

    @Override
    public void visitSqlQuerySelect(SqlQuerySelect node) {

        // 先分析from子句，确保所有的表都有alias
        SqlFrom from = node.getFrom();
        if (from != null) {
            visitSqlFrom(from);

            addTableFilter(node);
        } else {
            if (node.getWhere() != null || node.getHaving() != null || node.getOrderBy() != null
                    || node.getGroupBy() != null || node.getLimit() != null)
                throw new NopException(ERR_EQL_QUERY_NO_FROM_CLAUSE).source(node);

            // 如果没有指定from，则需要根据decorator来确定querySpace
            String querySpace = findQuerySpaceWhenNoFrom(node);
            this.querySpace = querySpace;
            this.dialect = context.getDialectForQuerySpace(querySpace);
        }

        SqlTableScope scope = new SqlTableScope(node, currentScope);
        node.setTableScope(scope);
        if (from != null) {
            addAliasToScope(scope, from);
        }
        currentScope = scope;

        // 分析所有的projection，并且确保每一列都有一个别名
        if (node.getProjections().isEmpty()) {
            // select *
            if (!node.getSelectAll())
                throw new NopException(ERR_EQL_SELECT_NO_PROJECTIONS).source(node);

            List<SqlProjection> items = getAllProjections(node);
            node.setProjections(items);
        } else {
            visitChildren(node.getProjections());
        }

        resolveSelectFields(node);

        if (node.getWhere() != null)
            visitSqlWhere(node.getWhere());

        if (node.getGroupBy() != null) {
            visitSqlGroupBy(node.getGroupBy());
        }

        if (node.getHaving() != null) {
            visitSqlHaving(node.getHaving());
        }

        if (node.getOrderBy() != null) {
            visitSqlOrderBy(node.getOrderBy());
        }

        if (node.getLimit() != null) {
            visitSqlLimit(node.getLimit());
        }

        currentScope = scope.getParent();
    }

    void addTableFilter(SqlQuerySelect node) {
        addEntityFilter(node);
    }

    void collectDefaultEntityFilter(SqlSingleTableSource table, Consumer<SqlExpr> consumer) {
        ISqlTableMeta tableMeta = (ISqlTableMeta) table.getResolvedTableMeta();

        if (tableMeta.isUseLogicalDelete() && !context.isDisableLogicalDelete()) {
            consumer.accept(buildLogicalDeleteFilter(table, tableMeta));
        }

        IEntityModel entityModel = tableMeta.getEntityModel();
        if (entityModel.isUseTenant()) {
            SqlBinaryExpr expr = newTenantExpr(table, tableMeta);
            consumer.accept(expr);
        }

        if (tableMeta.hasFilter()) {
            for (OrmEntityFilterModel filter : tableMeta.getFilters()) {
                SqlBinaryExpr expr = newBinaryExpr(table, tableMeta, filter.getName(), filter.getValue());
                consumer.accept(expr);
            }
        }
    }

    SqlBinaryExpr newTenantExpr(SqlSingleTableSource table, ISqlTableMeta tableMeta) {
        IEntityModel entityModel = tableMeta.getEntityModel();
        SqlBinaryExpr expr = new SqlBinaryExpr();
        SqlColumnName colName = newColName(table, tableMeta, entityModel.getTenantColumn().getName());

        expr.setLeft(colName);
        expr.setOperator(SqlOperator.EQ);
        SqlParameterMarker param = new SqlParameterMarker();
        param.setSqlParamBuilder(TenantParamBuilder.INSTANCE);
        expr.setRight(param);
        return expr;
    }

    SqlBinaryExpr newBinaryExpr(SqlSingleTableSource table, ISqlTableMeta tableMeta, String propName, Object value) {
        SqlBinaryExpr expr = new SqlBinaryExpr();
        SqlColumnName colName = newColName(table, tableMeta, propName);
        expr.setLeft(colName);
        expr.setOperator(SqlOperator.EQ);

        expr.setRight(EqlASTBuilder.literal(value));
        return expr;
    }

    SqlColumnName newColName(SqlSingleTableSource table, ISqlTableMeta tableMeta, String propName) {
        SqlColumnName col = EqlASTBuilder.colName(table.getAliasName(), propName);
        col.setTableSource(table);

        col.setResolvedExprMeta(tableMeta.getFieldExprMeta(propName, context.isAllowUnderscoreName()));
        return col;
    }

    SqlBinaryExpr buildLogicalDeleteFilter(SqlSingleTableSource table, ISqlTableMeta tableMeta) {
        Object value = tableMeta.getDeleteFlagValue(false, dialect);
        return newBinaryExpr(table, tableMeta, tableMeta.getDeleteFlagPropName(), value);
    }

    void addEntityFilter(SqlQuerySelect node) {
        SqlFrom from = node.getFrom();

        for (SqlSingleTableSource table : from.getEntitySources()) {
            if (!table.isFilterAlreadyAdded()) {
                table.setFilterAlreadyAdded(true);
                collectDefaultEntityFilter(table, filter -> node.makeWhere().appendFilter(filter));
            } else if (table.isMainSource()) {
                // 对于left join和right join，主表的缺省过滤条件需要写在where段
                collectDefaultEntityFilter(table, filter -> node.makeWhere().appendFilter(filter));
            }
        }
    }

    String findQuerySpaceWhenNoFrom(SqlQuerySelect node) {
        SqlDecorator decorator = node.getDecorator(OrmEqlConstants.DECORATOR_QUERY_SPACE);
        if (decorator == null)
            return DaoConstants.DEFAULT_QUERY_SPACE;
        if (decorator.getArgs() == null || decorator.getArgs().size() != 1)
            throw new NopException(ERR_EQL_DECORATOR_ARG_COUNT_IS_NOT_EXPECTED).source(decorator)
                    .param(ARG_DECORATOR, decorator.getName()).param(ARG_EXPECTED_COUNT, 1);
        SqlLiteral literal = decorator.getArgs().get(0);
        if (!(literal instanceof SqlStringLiteral))
            throw new NopException(ERR_EQL_DECORATOR_ARG_TYPE_IS_NOT_EXPECTED).source(decorator)
                    .param(ARG_DECORATOR, decorator.getName()).param(ARG_ARG_INDEX, 0).param(ARG_EXPECTED, "String");
        return ((SqlStringLiteral) literal).getValue();
    }

    /**
     * 向scope中注册数据库表的别名
     */
    void addAliasToScope(SqlTableScope scope, SqlFrom node) {
        for (SqlTableSource table : node.getTableSources()) {
            addAliasToScope(scope, table);
        }
    }

    void addAliasToScope(SqlTableScope scope, SqlTableSource table) {
        if (table instanceof SqlSingleTableSource) {
            SqlSingleTableSource source = (SqlSingleTableSource) table;
            scope.addTable(source.getScopeName(), source);
        } else if (table instanceof SqlSubqueryTableSource) {
            // lateral 表示可以看到同级的表
            SqlSubqueryTableSource source = (SqlSubqueryTableSource) table;
            scope.addTable(source.getAlias().getAlias(), source);
        } else if (table instanceof SqlJoinTableSource) {
            SqlJoinTableSource source = (SqlJoinTableSource) table;
            addAliasToScope(scope, source.getLeft());
            addAliasToScope(scope, source.getRight());
        }
    }

    @Override
    public void visitSqlFrom(SqlFrom node) {
        SqlTableScope tableScope = new SqlTableScope(node, currentScope);
        node.setTableScope(tableScope);

        for (SqlTableSource table : node.getTableSources()) {
            markMainSource(table);
        }

        for (SqlTableSource table : node.getTableSources()) {
            visitTableSource(tableScope, table);
        }
    }

    void markMainSource(SqlTableSource table) {
        if (table instanceof SqlSingleTableSource) {
            ((SqlSingleTableSource) table).setMainSource(true);
        } else if (table instanceof SqlJoinTableSource) {
            SqlJoinTableSource join = (SqlJoinTableSource) table;
            if (join.getJoinType() == SqlJoinType.LEFT_JOIN) {
                markMainSource(join.getLeft());
            } else if (join.getJoinType() == SqlJoinType.RIGHT_JOIN) {
                markMainSource(join.getRight());
            } else if (join.getJoinType() == SqlJoinType.FULL_JOIN) {
                //
                markMainSource(join.getLeft());
                markMainSource(join.getRight());
            }
        }
    }

    void visitTableSource(SqlTableScope tableScope, SqlTableSource table) {
        if (table instanceof SqlSingleTableSource) {
            SqlSingleTableSource source = (SqlSingleTableSource) table;
            resolveEntity(source);
            tableScope.addTable(source.getScopeName(), source);
        } else if (table instanceof SqlSubqueryTableSource) {
            // lateral 表示可以看到同级的表
            SqlSubqueryTableSource source = (SqlSubqueryTableSource) table;
            source.setAlias(makeTableAlias(source.getAlias()));

            if (source.getLateral()) {
                SqlTableScope oldScope = currentScope;
                currentScope = tableScope;
                visitSqlSubqueryTableSource(source);
                currentScope = oldScope;
            } else {
                visitSqlSubqueryTableSource(source);
            }
            tableScope.addTable(source.getAliasName(), source);
        } else if (table instanceof SqlJoinTableSource) {
            SqlJoinTableSource source = (SqlJoinTableSource) table;
            boolean hasCondition = source.getCondition() != null;
            visitJoinLeft(tableScope, source);
            visitJoinRight(tableScope, source);

            if (hasCondition) {
                SqlTableScope oldScope = currentScope;
                currentScope = tableScope;
                visitJoinCondition(source);
                currentScope = oldScope;
            }
            addTableFilterForJoin(source);
        }
    }

    void addTableFilterForJoin(SqlJoinTableSource source) {
        if (source.getLeft().isEntityTableSource()) {
            addTableFilterForJoinTable((SqlSingleTableSource) source.getLeft(), source);
        }

        if (source.getRight().isEntityTableSource()) {
            addTableFilterForJoinTable((SqlSingleTableSource) source.getRight(), source);
        }
    }

    void addTableFilterForJoinTable(SqlSingleTableSource table, SqlJoinTableSource join) {
        if (table.isFilterAlreadyAdded())
            return;

        table.setFilterAlreadyAdded(true);

        collectDefaultEntityFilter(table, join::addConditionFilter);
    }

    void visitJoinLeft(SqlTableScope tableScope, SqlJoinTableSource source) {
        SqlTableSource left = source.getLeft();
        if (left instanceof SqlSingleTableSource) {
            SqlSingleTableSource table = (SqlSingleTableSource) left;
            table.setAlias(makeTableAlias(table.getAlias()));

            tableScope.addTable(table.getAliasName(), table);

            SqlTableName tableName = table.getTableName();
            String fullName = tableName.getFullName();
            ISqlTableMeta entityMeta = context.resolveEntityTableMeta(fullName);
            if (entityMeta != null) {
                addResolvedEntity(source.getLocation(), entityMeta);
                // 对应全类名或者简单类名
                tableName.setResolvedTableMeta(entityMeta);
            } else {
                // 如果不是实体，检查是否是with xx as()语句所定义的内嵌视图
                SqlQualifiedName owner = tableName.getOwner();
                if (owner == null) {
                    SqlSelect cte = getCte(source, tableName.getName());
                    if (cte != null) {
                        resolveSelectFields(cte);
                        tableName.setResolvedTableMeta(cte.getResolvedTableMeta());
                        tableName.setResolvedCte(cte);

                        SqlSubqueryTableSource querySource = newCteSource(table, cte);
                        if (!dialect.isSupportWithAsClause()) {
                            source.setLeft(querySource);
                        } else {
                            addAliasToScope(currentScope, querySource);
                        }
                        return;
                    }
                }
                throw new NopException(ERR_EQL_UNKNOWN_ENTITY_NAME).source(source).param(ARG_ENTITY_NAME, fullName);
            }
        } else {
            visitTableSource(tableScope, left);
        }
    }

    SqlSubqueryTableSource newCteSource(SqlSingleTableSource table, SqlSelect cte) {
        SqlSubqueryTableSource querySource = new SqlSubqueryTableSource();
        querySource.setLocation(table.getLocation());
        querySource.setQuery(cte.deepClone());
        SqlAlias name = new SqlAlias();
        name.setAlias(table.getTableName().getName());
        querySource.setAlias(name);
        return querySource;
    }

    void resolveSelectFields(SqlSelect select) {
        if (select.getResolvedTableMeta() != null)
            return;

        ExprTypeResolver typeResolver = new ExprTypeResolver(dialect);
        Map<String, ISqlExprMeta> fields = new HashMap<>();
        for (SqlProjection projection : select.getProjections()) {
            SqlExprProjection proj = (SqlExprProjection) projection;
            String fieldName = EqlHelper.getFieldName(proj);
            typeResolver.resolveExprMeta(proj.getExpr());
            fields.put(fieldName, proj.getExpr().getResolvedExprMeta());
        }
        select.setResolvedTableMeta(new SelectResultTableMeta(fields));
    }

    // 将 UserInfo o left join o.dept 这种形式变换为 UserInfo o left join Department d on o.xx=d.xx
    void visitJoinRight(SqlTableScope tableScope, SqlJoinTableSource source) {
        SqlTableSource right = source.getRight();
        if (right instanceof SqlSingleTableSource) {
            SqlSingleTableSource table = (SqlSingleTableSource) right;
            table.setAlias(makeTableAlias(table.getAlias()));

            tableScope.addTable(table.getAliasName(), table);

            SqlTableName tableName = table.getTableName();
            String fullName = tableName.getFullName();
            ISqlTableMeta entityMeta = context.resolveEntityTableMeta(fullName);
            if (entityMeta != null) {
                // 对应全类名或者简单类名
                tableName.setResolvedTableMeta(entityMeta);
                if (source.getCondition() == null) {
                    throw new NopException(ERR_EQL_JOIN_NO_CONDITION).source(source).param(ARG_ENTITY_NAME, fullName);
                }
            } else {
                // 不是实体对象，需要检查是否是属性表达式
                SqlQualifiedName owner = tableName.getOwner();
                if (owner == null) {
                    // 如果没有owner，则检查是否是with xx as 语句引入的内嵌视图
                    SqlSelect cte = getCte(source, tableName.getName());
                    if (cte != null) {
                        tableName.setResolvedCte(cte);
                        tableName.setResolvedTableMeta(cte.getResolvedTableMeta());

                        SqlSubqueryTableSource querySource = newCteSource(table, cte);
                        if (!dialect.isSupportWithAsClause()) {
                            source.setRight(querySource);
                        } else {
                            addAliasToScope(currentScope, querySource);
                        }
                        return;
                    }
                    throw new NopException(ERR_EQL_UNKNOWN_ENTITY_NAME).source(source).param(ARG_ENTITY_NAME, fullName);
                }

                if (source.getCondition() != null) {
                    throw new NopException(ERR_EQL_PROP_PATH_JOIN_NOT_ALLOW_CONDITION).source(source)
                            .param(ARG_PROP_PATH, fullName).param(ARG_ENTITY_NAME, fullName);
                }

                PropPath propPath = table.getTableName().toPropPath();

                // 将属性关联表达式转换为表关联
                // 例如将left join o.dept d 替换为 left join Department d on o.deptId = d.id
                SqlPropJoin join = resolvePropPath(tableScope, right.getLocation(), propPath);
                if (join.getRight().getASTParent() != null) {
                    throw new NopException(ERR_EQL_JOIN_PROP_PATH_IS_DUPLICATED).source(source).param(ARG_PROP_PATH,
                            propPath.toString());
                }
                join.setExplicit(true);
                source.setRight(join.getRight());
                source.setCondition(join.getCondition());
            }
        } else {
            visitTableSource(tableScope, source.getRight());
        }
    }

    void addResolvedEntity(SourceLocation loc, ISqlTableMeta tableMeta) {
        readEntityModels.add(tableMeta.getEntityName());

        String querySpace = tableMeta.getQuerySpace();
        querySpaceToEntityNames.put(querySpace, tableMeta.getEntityName());
        if (querySpaceToEntityNames.size() > 1)
            throw new NopException(ERR_EQL_NOT_ALLOW_MULTIPLE_QUERY_SPACE).loc(loc)
                    .param(ARG_ENTITY_NAME, tableMeta.getEntityName()).param(ARG_QUERY_SPACE_MAP, querySpaceToEntityNames);

        this.querySpace = querySpace;

        if (dialect == null) {
            dialect = context.getDialectForQuerySpace(querySpace);

            if (dialect == null)
                throw new NopException(ERR_EQL_UNKNOWN_QUERY_SPACE).loc(loc).param(ARG_QUERY_SPACE, querySpace)
                        .param(ARG_ENTITY_NAME, tableMeta.getEntityName());
        }
    }

    SqlPropJoin resolvePropPath(SqlTableScope scope, SourceLocation loc, PropPath propPath) {
        // 例如 left join o.dept 未找到o对应的数据源定义
        SqlTableSource left = scope.getTableByAlias(propPath.getName());
        if (left == null) {
            throw new NopException(ERR_EQL_UNKNOWN_ALIAS).loc(loc).param(ARG_ALIAS, propPath.getName())
                    .param(ARG_PROP_PATH, propPath.toString());
        }

        if (!(left instanceof SqlSingleTableSource))
            throw new NopException(ERR_EQL_OWNER_NOT_REF_TO_ENTITY).loc(loc).param(ARG_LEFT_SOURCE, left)
                    .param(ARG_ALIAS, propPath.getName()).param(ARG_PROP_PATH, propPath.toString());

        return resolvePropPath((SqlSingleTableSource) left, propPath.getNext());
    }

    SqlPropJoin resolvePropPath(SqlSingleTableSource source, PropPath propPath) {
        SqlPropJoin join = source.getPropJoin(propPath.getName());
        if (join != null) {
            join.incRef();
        } else {
            ISqlSelectionMeta table = source.getTableName().getResolvedTableMeta();
            if (!(table instanceof ISqlTableMeta)) {
                throw new NopException(ERR_EQL_OWNER_NOT_REF_TO_ENTITY).source(source).param(ARG_TABLE,
                        source.getTableName().getFullName());
            }

            ISqlTableMeta tableMeta = (ISqlTableMeta) table;

            ISqlExprMeta fieldExpr = table.getFieldExprMeta(propPath.getName(), context.isAllowUnderscoreName());
            if (fieldExpr != null) {
                IOrmDataType dataType = fieldExpr.getOrmDataType();
                if (!dataType.getKind().isRelation())
                    throw new NopException(ERR_EQL_PROP_PATH_NOT_VALID_TO_ONE_REFERENCE).source(source)
                            .param(ARG_PROP_NAME, propPath.getName()).param(ARG_ENTITY_NAME, tableMeta.getEntityName());

                IEntityRelationModel ref = (IEntityRelationModel) dataType;
                if (ref.isToOneRelation()) {
                    join = addToOneRelationJoin(source, ref);
                } else {
                    if (ref.getKeyProp() == null)
                        throw new NopException(ERR_EQL_PROP_PATH_NOT_VALID_TO_ONE_REFERENCE).source(source)
                                .param(ARG_PROP_NAME, propPath.getName()).param(ARG_ENTITY_NAME, tableMeta.getEntityName());

                    if (propPath.getNext() == null)
                        throw new NopException(ERR_EQL_PROP_PATH_NOT_VALID_TO_ONE_REFERENCE).source(source)
                                .param(ARG_PROP_NAME, propPath.getName()).param(ARG_ENTITY_NAME, tableMeta.getEntityName());

                    String propJoinName = propPath.getName() + '.' + propPath.getNext().getName();
                    join = source.getPropJoin(propJoinName);
                    propPath = propPath.getNext();
                    if (join != null) {
                        join.incRef();
                    } else {
                        // to-many关联，且具有keyProp属性
                        join = addToManyRelationJoin(source, ref, propJoinName, propPath.getName());
                    }
                }
            } else {
                // 如果不是实体的直接属性，检查是否是复合属性的别名
                join = requireAliasPropPathForRef(source, tableMeta, propPath.getName());
            }
        }

        if (propPath.getNext() == null)
            return join;

        return resolvePropPath(join.getRight(), propPath.getNext());
    }

    SqlPropJoin requireAliasPropPathForRef(SqlSingleTableSource source, ISqlTableMeta tableMeta, String aliasName) {
        PropPath aliasPath = tableMeta.getAliasPropPath(aliasName);
        if (aliasPath != null) {
            return resolvePropPath(source, aliasPath);
        } else {
            throw new NopException(ERR_EQL_PROP_PATH_NOT_VALID_TO_ONE_REFERENCE).source(source)
                    .param(ARG_PROP_PATH, aliasName).param(ARG_ENTITY_NAME, tableMeta.getEntityName());
        }
    }

    SqlPropJoin addToOneRelationJoin(SqlSingleTableSource source, IEntityRelationModel ref) {
        if (ref.isDynamicJoin())
            return addToOneDynamicRelationJoin(source, ref);

        SqlSingleTableSource refTable = makeTableSource(source.getLocation(), ref.getRefEntityModel(), null);
        SqlExpr condition = makeCondition(ref.getJoin(), source, refTable);

        refTable.setForPropJoin(true);
        SqlPropJoin join = new SqlPropJoin();
        join.setLeft(source);
        join.setRight(refTable);
        join.setJoinType(SqlJoinType.JOIN); // 暂时设置为inner join

        // 如果仅在order by中使用，且属性可空，则使用left join
        if (inOrderBy && !ref.isMandatory()) {
            join.setJoinType(SqlJoinType.LEFT_JOIN);
        }

        join.setExplicit(false);
        join.setCondition(condition);

        addTableFilterForPropJoin(join);

        source.addPropJoin(ref.getName(), join);
        return join;
    }

    void addTableFilterForPropJoin(SqlPropJoin join) {
        if (join.getLeft().isEntityTableSource()) {
            addTableFilterForPropJoinTable(join.getLeft(), join);
        }

        if (join.getRight().isEntityTableSource()) {
            addTableFilterForPropJoinTable(join.getRight(), join);
        }
    }

    void addTableFilterForPropJoinTable(SqlSingleTableSource table, SqlPropJoin join) {
        if (table.isFilterAlreadyAdded())
            return;

        table.setFilterAlreadyAdded(true);
        ISqlTableMeta tableMeta = (ISqlTableMeta) table.getResolvedTableMeta();
        if (tableMeta.isUseLogicalDelete()) {
            SqlBinaryExpr expr = buildLogicalDeleteFilter(table, tableMeta);
            join.addConditionFilter(expr);
        }
    }

    SqlPropJoin addToOneDynamicRelationJoin(SqlSingleTableSource source, IEntityRelationModel ref) {
        SqlSingleTableSource refTable = makeTableSource(source.getLocation(), ref.getRefEntityModel(), null);

        SqlSingleTableSource leftSource = source, rightSource = refTable;

        SqlExpr condition = null;
        if (ref.getJoin().size() != 1) {
            throw new NopException(ERR_EQL_NOT_SUPPORT_MULTI_JOIN_ON_ALIAS)
                    .param(ARG_ENTITY_NAME, ref.getOwnerEntityModel().getName())
                    .param(ARG_PROP_NAME, ref.getName());
        }

        IEntityJoinConditionModel on = ref.getJoin().get(0);

        IEntityPropModel leftProp = on.getLeftPropModel();
        IEntityPropModel rightProp = on.getRightPropModel();

        if (leftProp != null) {
            if (leftProp.isAliasModel()) {
                PropPath propPath = PropPath.parse(leftProp.getAliasPropPath());
                if (propPath.getNext() == null) {
                    leftProp = on.getLeftPropModel().getOwnerEntityModel().getColumn(propPath.getName(), false);
                } else {
                    SqlPropJoin join = resolvePropPath(source, propPath.getOwner());
                    leftSource = join.getRight();
                    leftProp = leftSource.getResolvedTableMeta().requirePropMeta(propPath.getLast(), context.isAllowUnderscoreName());
                }
            }
        }
        if (rightProp != null) {
            if (rightProp.isAliasModel()) {
                PropPath propPath = PropPath.parse(rightProp.getAliasPropPath());
                if (propPath.getNext() == null) {
                    rightProp = on.getRightPropModel().getOwnerEntityModel().getColumn(propPath.getName(), false);
                } else {
                    SqlPropJoin join = resolvePropPath(refTable, propPath.getOwner());
                    rightSource = join.getRight();
                    rightProp = rightSource.getResolvedTableMeta().requirePropMeta(propPath.getLast(), context.isAllowUnderscoreName());
                }
            }

            SqlBinaryExpr eq = new SqlBinaryExpr();
            eq.setOperator(SqlOperator.EQ);
            eq.setLeft(makePropExpr(leftSource, leftProp, null));
            eq.setRight(makePropExpr(rightSource, rightProp, null));
            condition = eq;
        }

        rightSource.setForPropJoin(true);
        SqlPropJoin join = new SqlPropJoin();
        join.setLeft(leftSource);
        join.setRight(rightSource);
        join.setJoinType(SqlJoinType.JOIN); // 暂时设置为inner join

        // 如果仅在order by中使用，且属性可空，则使用left join
        if (inOrderBy && !ref.isMandatory()) {
            join.setJoinType(SqlJoinType.LEFT_JOIN);
        }

        join.setExplicit(false);
        join.setCondition(condition);

        addTableFilterForPropJoin(join);

        leftSource.addPropJoin(ref.getName(), join);
        return join;
    }

    SqlPropJoin addToManyRelationJoin(SqlSingleTableSource source, IEntityRelationModel ref, String propJoinName,
                                      String keyValue) {
        IColumnModel keyCol = ref.getRefEntityModel().getColumn(ref.getKeyProp(), false);
        SqlSingleTableSource refTable = makeTableSource(source.getLocation(), ref.getRefEntityModel(), null);
        SqlExpr joinCondition = makeCondition(ref.getJoin(), source, refTable);
        SqlAndExpr condition = new SqlAndExpr();
        condition.setLeft(joinCondition);
        condition.setRight(makeEqExpr(refTable, keyCol, keyValue));

        refTable.setForPropJoin(true);
        SqlPropJoin join = new SqlPropJoin();
        join.setLeft(source);
        join.setRight(refTable);
        // 扩展字段有可能没有被初始化，因此这里总是使用left join
        join.setJoinType(SqlJoinType.LEFT_JOIN);

        join.setExplicit(false);
        join.setCondition(condition);

        addTableFilterForPropJoin(join);

        source.addPropJoin(propJoinName, join);
        return join;
    }

    SqlExpr makeEqExpr(SqlTableSource source, IColumnModel keyCol, String keyValue) {
        SqlBinaryExpr expr = new SqlBinaryExpr();
        expr.setOperator(SqlOperator.EQ);
        SqlColumnName col = new SqlColumnName();
        SqlQualifiedName owner = new SqlQualifiedName();
        owner.setName(source.getAliasName());
        col.setOwner(owner);
        col.setTableSource(source);
        col.setResolvedExprMeta(source.getResolvedTableMeta().getFieldExprMeta(keyCol.getName(), context.isAllowUnderscoreName()));
        expr.setLeft(col);
        SqlStringLiteral value = new SqlStringLiteral();
        value.setValue(keyValue);
        expr.setRight(value);
        return expr;
    }

    SqlSingleTableSource makeTableSource(SourceLocation loc, IEntityModel entityModel, SqlAlias alias) {
        readEntityModels.add(entityModel.getName());

        SqlSingleTableSource table = new SqlSingleTableSource();
        table.setLocation(loc);
        SqlTableName tableName = new SqlTableName();
        ISqlSelectionMeta tableMeta = context.resolveEntityTableMeta(entityModel.getName());
        if (tableMeta == null)
            throw new NopException(ERR_EQL_UNKNOWN_ENTITY_NAME).param(ARG_ENTITY_NAME, entityModel.getName());
        tableName.setResolvedTableMeta(tableMeta);

        tableName.setName(entityModel.getName());
        table.setTableName(tableName);

        alias = makeTableAlias(alias);
        alias.setASTParent(null);
        table.setAlias(alias);
        return table;
    }

    SqlExpr makeCondition(List<? extends IEntityJoinConditionModel> condition, SqlTableSource leftSource,
                          SqlTableSource rightSource) {
        if (condition.size() == 1) {
            return makeConditionExpr(condition.get(0), leftSource, rightSource);
        } else {
            SqlAndExpr and = new SqlAndExpr();
            and.setLeft(makeConditionExpr(condition.get(0), leftSource, rightSource));
            and.setRight(makeCondition(condition.subList(1, condition.size()), leftSource, rightSource));
            return and;
        }
    }

    SqlExpr makeConditionExpr(IEntityJoinConditionModel condition, SqlTableSource leftSource,
                              SqlTableSource rightSource) {
        SqlBinaryExpr eq = new SqlBinaryExpr();
        eq.setOperator(SqlOperator.EQ);
        eq.setLeft(makePropExpr(leftSource, condition.getLeftPropModel(), condition.getLeftValue()));
        eq.setRight(makePropExpr(rightSource, condition.getRightPropModel(), condition.getRightValue()));
        return eq;
    }

    SqlExpr makePropExpr(SqlTableSource source, IEntityPropModel propModel, Object value) {
        if (propModel != null) {
            SqlColumnName col = new SqlColumnName();
            col.setPropModel(propModel);
            col.setName(propModel.getName());
            col.setTableSource(source);
            col.setResolvedExprMeta(source.getResolvedTableMeta().requireFieldExprMeta(propModel.getName(), context.isAllowUnderscoreName()));
            SqlQualifiedName name = new SqlQualifiedName();
            name.setName(source.getAliasName());
            col.setOwner(name);
            return col;
        } else {
            if (value instanceof Number) {
                SqlNumberLiteral literal = new SqlNumberLiteral();
                literal.setResolvedExprMeta(SingleColumnExprMeta.valueExprMeta(StdSqlType.DECIMAL));
                literal.setValue(StringHelper.toString(value, null));
                return literal;
            } else {
                SqlStringLiteral literal = new SqlStringLiteral();
                literal.setResolvedExprMeta(SingleColumnExprMeta.valueExprMeta(StdSqlType.VARCHAR));
                literal.setValue(StringHelper.toString(value, null));
                return literal;
            }
        }
    }

    // void visitLateral(SqlTableScope joinScope, SqlTableScope lateralScope, SqlTableSource table) {
    // if (table instanceof SqlSubqueryTableSource) {
    // // lateral 表示可以看到同级的表
    // SqlSubqueryTableSource source = (SqlSubqueryTableSource) table;
    //
    // if (source.getLateral()) {
    // currentScope = lateralScope;
    // visitSqlSubqueryTableSource(source);
    // currentScope = lateralScope.getParent();
    // lateralScope.addTable(source.getAlias().getAlias(), source);
    // } else {
    // joinScope.addTable(source.getAlias().getAlias(), source);
    // }
    // } else if (table instanceof SqlJoinTableSource) {
    // SqlJoinTableSource source = (SqlJoinTableSource) table;
    // visitLateral(joinScope, lateralScope, source.getLeft());
    // visitLateral(joinScope, lateralScope, source.getRight());
    //
    // currentScope = joinScope;
    // visitJoinCondition(source);
    // currentScope = joinScope.getParent();
    // }
    // }

    void resolveEntity(SqlSingleTableSource table) {
        SqlTableName tableName = table.getTableName();
        String fullName = tableName.getFullName();
        ISqlTableMeta tableMeta = context.resolveEntityTableMeta(fullName);
        if (tableMeta != null) {
            table.setAlias(makeTableAlias(table.getAlias()));
            addResolvedEntity(tableName.getLocation(), tableMeta);
            // 对应全类名或者简单类名
            tableName.setResolvedTableMeta(tableMeta);
        } else {
            // 不是实体对象，需要检查是否是属性表达式
            SqlQualifiedName owner = tableName.getOwner();
            if (owner == null) {
                SqlSelect cte = getCte(table, tableName.getName());
                if (table.getAlias() == null) {
                    SqlAlias alias = new SqlAlias();
                    alias.setAlias(tableName.getName());
                    table.setAlias(alias);
                }
                if (cte != null) {
                    resolveSelectFields(cte);
                    tableName.setResolvedCte(cte);
                    tableName.setResolvedTableMeta(cte.getResolvedTableMeta());

                    SqlSubqueryTableSource querySource = newCteSource(table, cte);
                    if (!dialect.isSupportWithAsClause()) {
                        table.getASTParent().replaceChild(table, querySource);
                    } else {
                        addAliasToScope(currentScope, querySource);
                    }
                    return;
                }
            }
            throw new NopException(ERR_EQL_UNKNOWN_ENTITY_NAME).source(table).param(ARG_ENTITY_NAME, fullName);
        }
    }

    void visitJoinCondition(SqlJoinTableSource source) {
        if (source.getCondition() != null) {
            visit(source.getCondition());
        }
    }

    SqlAlias makeTableAlias(SqlAlias alias) {
        if (alias == null) {
            do {
                String newName = aliasGenerator.genTableAlias();
                if (!assignedAliases.contains(newName)) {
                    alias = new SqlAlias();
                    alias.setGenerated(true);
                    alias.setAlias(newName);
                    return alias;
                }
            } while (true);
        }
        return alias;
    }

    @Override
    public void visitSqlAllProjection(SqlAllProjection node) {
        SqlQualifiedName owner = node.getOwner();
        if (owner != null) {
            if (owner.getNext() == null) {
                SqlTableSource source = currentScope.getTableByAlias(owner.getName());
                if (source != null) {
                    replaceAllProjection(node, getSourceSelectItems(source));
                    return;
                }
            }

            SqlPropJoin join = resolvePropPath(currentScope, owner.getLocation(), owner.toPropPath());
            // 加载关联实体的所有字段
            replaceAllProjection(node, getSourceSelectItems(join.getRight()));
        } else {
            SqlQuerySelect select = getQuerySelect(node);

            List<SqlProjection> items = getAllProjections(select);
            replaceAllProjection(node, items);
        }
    }

    List<SqlProjection> getAllProjections(SqlQuerySelect select) {
        SqlFrom from = select.getFrom();
        if (from == null)
            throw new NopException(ERR_EQL_QUERY_NO_FROM_CLAUSE).source(select);

        List<SqlProjection> items = new ArrayList<>();
        for (SqlTableSource source : from.getTableSources()) {
            items.addAll(getSourceSelectItems(source));
        }
        return items;
    }

    void replaceAllProjection(SqlAllProjection proj, List<SqlProjection> projs) {
        SqlQuerySelect select = (SqlQuerySelect) proj.getASTParent();
        List<SqlProjection> replaced = select.getProjections().stream().flatMap(p -> {
            if (p == proj) {
                return projs.stream();
            } else {
                return Stream.of(p);
            }
        }).collect(Collectors.toList());
        select.setProjections(replaced);
        super.visitChildren(select.getProjections());
    }

    List<SqlProjection> getSourceSelectItems(SqlTableSource source) {
        if (source instanceof SqlSingleTableSource) {
            SqlSingleTableSource table = (SqlSingleTableSource) source;
            return buildSelectItems(table, table.getSourceSelect() == null);
        } else if (source instanceof SqlSubqueryTableSource) {
            SqlSubqueryTableSource query = (SqlSubqueryTableSource) source;
            resolveSelectFields(query.getQuery());

            return buildSelectItems(source, false);
        } else {
            throw new IllegalStateException("nop.err.invalid-source:" + source);
        }
    }

    List<SqlProjection> buildSelectItems(SqlTableSource source, boolean onlyColumn) {
        ISqlSelectionMeta tableMeta = source.getResolvedTableMeta();
        Map<String, ISqlExprMeta> fieldMetas = tableMeta.getFieldExprMetas();
        List<SqlProjection> ret = new ArrayList<>(fieldMetas.size());
        for (Map.Entry<String, ISqlExprMeta> entry : fieldMetas.entrySet()) {
            ISqlExprMeta exprMeta = entry.getValue();
            if (onlyColumn) {
                if (!exprMeta.getOrmDataType().getKind().isColumn())
                    continue;
            }
            SqlExprProjection proj = new SqlExprProjection();
            SqlColumnName col = newColNameWithType(source, entry.getKey(), exprMeta);

            proj.setExpr(col);
            proj.setAlias(newColumnAlias());
            ret.add(proj);
        }
        return ret;
    }

    SqlColumnName newColNameWithType(SqlTableSource source, String name, ISqlExprMeta exprMeta) {
        SqlColumnName colName = new SqlColumnName();
        colName.setTableSource(source);
        SqlQualifiedName qn = new SqlQualifiedName();
        qn.setResolvedSource(source);
        qn.setName(source.getAliasName());
        colName.setOwner(qn);
        colName.setName(name);
        colName.setResolvedExprMeta(exprMeta);
        return colName;
    }

    @Override
    public void visitSqlExprProjection(SqlExprProjection node) {
        if (node.getAlias() == null) {
            node.setAlias(newColumnAlias());
        }
        visit(node.getExpr());
    }

    @Override
    public void visitSqlColumnName(SqlColumnName node) {
        SqlTableSource source;
        if (node.getOwner() == null) {
            resolveDefaultTableSource(node);
            return;
        } else if (node.getOwner().getNext() == null) {
            source = currentScope.getTableByAlias(node.getOwner().getName());
            if (source == null) {
                throw new NopException(ERR_EQL_UNKNOWN_ALIAS).source(node).param(ARG_ALIAS, node.getOwner().getName());
            }
        } else {
            SqlPropJoin join = resolvePropPath(currentScope, node.getLocation(), node.getOwner().toPropPath());
            source = join.getRight();
        }
        resolveColName(source, node);
    }

    private void resolveDefaultTableSource(SqlColumnName node) {
        // 没有使用表的别名，例如 select name from UserInfo
        SqlQuerySelect select = getQuerySelect(node);
        if (select == null) {
            ISqlTableSourceSupport tableSupport = resolveTableSource(node);
            if (tableSupport != null) {
                resolveColName(tableSupport.getResolvedTableSource(), node);
                return;
            }
        }

        if (select == null || select.getFrom() == null) {
            throw new NopException(ERR_EQL_QUERY_NO_FROM_CLAUSE)
                    .source(node).param(ARG_COL_NAME, node.getName());
        }

        SqlTableSource source = currentScope.getTableByAlias(node.getName());
        if (source != null) {
            node.setTableSource(source);
            // 如果是参与比较表达式，则返回主键字段
            ISqlTableMeta tableMeta = getTableMeta(source);
            if (node.getASTParent().getASTKind() == EqlASTKind.SqlBinaryExpr) {
                ISqlExprMeta fieldMeta = tableMeta.getFieldExprMeta(OrmEqlConstants.PROP_ID, false);
                node.setResolvedExprMeta(fieldMeta);
                node.setPropModel((IEntityPropModel) fieldMeta.getOrmDataType());
            } else {
                node.setResolvedExprMeta(tableMeta.getEntityExprMeta());
            }
            return;
        }

        // 只考虑from语句的第一个表，而且只考虑单表选择的情况
        source = select.getFrom().getFirstTableSource();
        if (!(source instanceof SqlSingleTableSource)) {
            throw new NopException(ERR_EQL_UNKNOWN_COLUMN_NAME).source(node).param(ARG_COL_NAME, node.getName());
        }
        resolveColName(source, node);
    }

    ISqlTableMeta getTableMeta(SqlTableSource source) {
        if (source instanceof SqlSingleTableSource) {
            SqlSingleTableSource single = (SqlSingleTableSource) source;
            ISqlSelectionMeta tableMeta = single.getTableName().getResolvedTableMeta();
            if (tableMeta instanceof ISqlTableMeta)
                return (ISqlTableMeta) tableMeta;
        }
        throw new NopException(ERR_EQL_ONLY_SUPPORT_SINGLE_TABLE_SOURCE).source(source).param(ARG_TABLE_SOURCE,
                source.getDisplayString());
    }

    void resolveColName(SqlTableSource source, SqlColumnName node) {
        resolveColName(source, node, node.getName());
    }

    void resolveColName(SqlTableSource source, SqlColumnName node, String propName) {
        ISqlSelectionMeta model = source.getResolvedTableMeta();
        if (model == null)
            throw new NopException(ERR_EQL_TABLE_SOURCE_NOT_RESOLVED).source(node)
                    .param(ARG_TABLE_SOURCE, source.toSQL().getText()).param(ARG_COL_NAME, propName);

        ISqlExprMeta propExpr = model.getFieldExprMeta(propName, context.isAllowUnderscoreName());
        if (propExpr != null) {
            node.setTableSource(source);
            node.setResolvedExprMeta(propExpr);

            // 如果是从子查询中选择，则列名改变为子查询结果集中的alias
            SqlSelect query = source.getSourceSelect();
            if (query != null) {
                SqlExprProjection proj = query.getProjectionByExprMeta(propExpr);
                List<String> colNames = new ArrayList<>(propExpr.getColumnCount());
                for (int i = 0, n = propExpr.getColumnCount(); i < n; i++) {
                    String colName = EqlHelper.getAlias(proj.getAlias().getAlias(), i, n);
                    colNames.add(colName);
                }
                node.setResolvedExprMeta(new RenamedSqlExprMeta(propExpr, colNames));
            }
        } else {
            PropPath propPath = model.getAliasPropPath(node.getName());
            if (propPath == null) {
                if (node.getOwner() == null && inOrderBy) {
                    // order by段中可以使用selection中的字段别名
                    SqlQuerySelect select = getQuerySelect(node);
                    ISqlExprMeta fieldExpr = select.getResolvedTableMeta().getFieldExprMeta(node.getName(), false);
                    if (fieldExpr != null) {
                        node.setResolvedExprMeta(fieldExpr);
                        node.setProjection(select.getProjectionByExprMeta(fieldExpr));
                        return;
                    }
                }
                if (model instanceof ISqlTableMeta) {
                    throw new NopException(ERR_EQL_UNKNOWN_COLUMN_NAME).source(node)
                            .param(ARG_ENTITY_NAME, ((ISqlTableMeta) model).getEntityName())
                            .param(ARG_COL_NAME, node.getName());
                } else {
                    throw new NopException(ERR_EQL_FIELD_NOT_IN_SUBQUERY).source(node).param(ARG_FIELD_NAME,
                            node.getName());
                }
            }

            if (propPath.getNext() == null) {
                resolveColName(source, node, propPath.getName());
            } else {
                SqlPropJoin join = resolvePropPath((SqlSingleTableSource) source, propPath.getOwner());
                resolveColName(join.getRight(), node, propPath.getLast());
            }
        }
    }

    @Override
    public void visitSqlRegularFunction(SqlRegularFunction node) {
        ISQLFunction fn = dialect.getFunction(node.getName());
        if (fn == null)
            throw new NopException(ERR_EQL_UNKNOWN_FUNCTION).source(node).param(ARG_FUNC_NAME, node.getName())
                    .param(ARG_DIALECT, dialect.getName());

        int argCount = node.getArgs().size();
        if (fn.getMinArgCount() > argCount) {
            throw new NopException(ERR_EQL_FUNC_TOO_FEW_ARGS).source(node).param(ARG_FUNC_NAME, node.getName())
                    .param(ARG_ARG_COUNT, argCount).param(ARG_MIN_ARG_COUNT, fn.getMinArgCount());
        }

        if (fn.getMaxArgCount() < node.getArgs().size()) {
            throw new NopException(ERR_EQL_FUNC_TOO_MANY_ARGS).source(node).param(ARG_FUNC_NAME, node.getName())
                    .param(ARG_ARG_COUNT, argCount).param(ARG_MAX_ARG_COUNT, fn.getMinArgCount());
        }
        node.setResolvedFunction(fn);

        visitChildren(node.getArgs());
    }

    private String genColumnAlias() {
        do {
            String alias = aliasGenerator.genColumnAlias();
            if (!assignedAliases.contains(alias))
                return alias;
        } while (true);
    }

    private SqlAlias newColumnAlias() {
        SqlAlias ret = new SqlAlias();
        ret.setGenerated(true);
        ret.setAlias(genColumnAlias());
        return ret;
    }

    @Override
    public void visitSqlInsert(SqlInsert node) {
        SqlTableName table = node.getTableName();

        currentScope = new SqlTableScope(node, currentScope);
        ISqlTableMeta tableMeta = resolveEntity(table);

        SqlSingleTableSource source = newSingleTableSource(table, null);
        node.setResolvedTableSource(source);

        writeEntityModel = tableMeta.getEntityName();

        visitChildren(node.getColumns());

        if (node.getSelect() != null) {
            visit(node.getSelect());
        }

        if (node.getValues() != null) {
            visit(node.getValues());
        }

        currentScope = currentScope.getParent();
    }

    ISqlTableMeta resolveEntity(SqlTableName table) {
        String fullName = table.getFullName();

        ISqlTableMeta tableMeta = context.resolveEntityTableMeta(fullName);

        if (tableMeta != null) {
            addResolvedEntity(table.getLocation(), tableMeta);
            // 对应全类名或者简单类名
            table.setResolvedTableMeta(tableMeta);
            return tableMeta;
        } else {
            throw new NopException(ERR_EQL_UNKNOWN_ENTITY_NAME).source(table).param(ARG_ENTITY_NAME, fullName);
        }
    }

    @Override
    public void visitSqlUpdate(SqlUpdate node) {
        SqlTableName table = node.getTableName();
        node.setAlias(makeTableAlias(node.getAlias()));

        currentScope = new SqlTableScope(node, currentScope);
        ISqlTableMeta tableMeta = resolveEntity(table);

        SqlSingleTableSource source = newSingleTableSource(table, node.getAlias().getAlias());
        node.setResolvedTableSource(source);

        writeEntityModel = tableMeta.getEntityName();

        this.visitChildren(node.getAssignments());
        this.visitChild(node.getWhere());
        currentScope = currentScope.getParent();
    }

    private SqlSingleTableSource newSingleTableSource(SqlTableName table, String alias) {
        SqlSingleTableSource source = new SqlSingleTableSource();
        source.setLocation(table.getLocation());
        source.setTableName(table.deepClone());
        source.getTableName().setResolvedTableMeta(table.getResolvedTableMeta());
        if (alias != null)
            currentScope.addTable(alias, source);
        return source;
    }

    @Override
    public void visitSqlDelete(SqlDelete node) {
        SqlTableName table = node.getTableName();
        node.setAlias(makeTableAlias(node.getAlias()));

        currentScope = new SqlTableScope(node, currentScope);

        ISqlTableMeta tableMeta = resolveEntity(table);
        writeEntityModel = tableMeta.getEntityName();

        SqlSingleTableSource source = newSingleTableSource(table, node.getAlias().getAlias());
        node.setResolvedTableSource(source);

        this.visitChild(node.getWhere());

        currentScope = currentScope.getParent();
    }

    @Override
    public void visitSqlOrderBy(SqlOrderBy node) {
        inOrderBy = true;
        super.visitSqlOrderBy(node);
        inOrderBy = false;
    }

    private SqlQuerySelect getQuerySelect(EqlASTNode node) {
        do {
            if (node instanceof SqlQuerySelect)
                return (SqlQuerySelect) node;
            node = node.getASTParent();
        } while (node != null);
        return null;
    }

    private ISqlTableSourceSupport resolveTableSource(EqlASTNode node) {
        do {
            if (node instanceof ISqlTableSourceSupport)
                return (ISqlTableSourceSupport) node;
            node = node.getASTParent();
        } while (node != null);
        return null;
    }

    private SqlSelectWithCte getSelectWithCte(EqlASTNode node) {
        do {
            if (node instanceof SqlSelectWithCte)
                return (SqlSelectWithCte) node;
            node = node.getASTParent();
        } while (node != null);
        return null;
    }

    private SqlSelect getCte(EqlASTNode node, String name) {
        do {
            SqlSelectWithCte selectWithCte = getSelectWithCte(node);
            if (selectWithCte == null)
                return null;
            SqlSelect cte = selectWithCte.getCte(name);
            if (cte != null)
                return cte;
            node = selectWithCte.getASTParent();
        } while (node != null);

        return null;
    }
}