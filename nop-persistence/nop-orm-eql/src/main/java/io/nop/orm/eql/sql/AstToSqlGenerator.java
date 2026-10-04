/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.sql;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.text.marker.Marker;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlExprList;
import io.nop.core.lang.sql.SyntaxMarker;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.SQLDataType;
import io.nop.dao.dialect.function.ISQLFunction;
import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.SqlBooleanLiteral;
import io.nop.orm.eql.ast.SqlCastExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlCteStatement;
import io.nop.orm.eql.ast.SqlDateTimeLiteral;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlJoinTableSource;
import io.nop.orm.eql.ast.SqlLikeExpr;
import io.nop.orm.eql.ast.SqlLimit;
import io.nop.orm.eql.ast.SqlOrderBy;
import io.nop.orm.eql.ast.SqlOrderByItem;
import io.nop.orm.eql.ast.SqlWindowDecl;
import io.nop.orm.eql.ast.SqlWindowExpr;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlRegularFunction;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlTypeExpr;
import io.nop.orm.eql.ast.SqlUnionSelect;
import io.nop.orm.eql.compile.ISqlTableSourceSupport;
import io.nop.orm.eql.compile.SqlPropJoin;
import io.nop.orm.eql.enums.SqlCompareRange;
import io.nop.orm.eql.enums.SqlJoinType;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.eql.meta.ISqlSelectionMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.utils.EqlHelper;
import io.nop.orm.model.IEntityModel;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static io.nop.orm.eql.OrmEqlErrors.ARG_AST_NODE;
import static io.nop.orm.eql.OrmEqlErrors.ARG_DIALECT;
import static io.nop.orm.eql.OrmEqlErrors.ARG_REASON;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_CROSS_JOIN_NOT_ALLOW_CONDITION;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_CTE_COLUMN_NAME_UNRESOLVED;
import static io.nop.orm.eql.OrmEqlErrors.ARG_FUNC_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_ILIKE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NULLS_ORDER_NOT_SUPPORTED;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNSUPPORTED_PROJECTION_TYPE;

public class AstToSqlGenerator extends AstToEqlGenerator {

    private final IDialect dialect;

    private final boolean enableFilter;

    private final Set<SqlSingleTableSource> filteredSources = CollectionHelper.newIdentityHashSet();

    public AstToSqlGenerator(IDialect dialect, boolean enableFilter, SQL.SqlBuilder sb) {
        super(sb);
        this.dialect = dialect;
        this.enableFilter = enableFilter;
    }

    public AstToSqlGenerator(IDialect dialect, boolean enableFilter) {
        this(dialect, enableFilter, SQL.begin());
    }

    protected String normalizeTableName(String tableName) {
        return EqlHelper.normalizeTableName(dialect, tableName);
    }

    @Override
    public void visitSqlQuerySelect(SqlQuerySelect node) {
        if (node.getLimit() != null) {
            SqlLimit limit = node.getLimit();
            SqlExprList exprs = dialect.getPaginationHandler().buildPageExpr(limit.getLimit(), limit.getOffset(),
                    new PrintSqlSelect(node));
            printExprList(exprs);
        } else {
            printSelect(node);
        }
    }

    class PrintSqlSelect extends SqlNodeWrapper {
        public PrintSqlSelect(SqlQuerySelect select) {
            super(select);
        }

        @Override
        public void appendTo(SQL.SqlBuilder buf) {
            AstToEqlGenerator gen = new AstToSqlGenerator(dialect, enableFilter, buf);
            gen.setPretty(true);
            gen.visit(getNode());
        }
    }

    protected void printSelect(SqlQuerySelect node) {
        if (node.getFrom() == null && node.getWhere() == null) {
            // select xx 语法
            AstToSqlGenerator gen = new AstToSqlGenerator(dialect, enableFilter);
            gen.setPretty(isPretty());
            gen.printList(node.getProjections(), ",");
            SQL sql = gen.getSql().end();
            String text = dialect.getSelectFromDualSql(sql.getText());
            sb.append(text);
            if (!sql.getMarkers().isEmpty()) {
                int offset = text.indexOf(sql.getText());
                for (Marker marker : sql.getMarkers()) {
                    sb.appendMarker(marker.offset(offset));
                }
            }
        } else {
            super.printSelect(node);
        }
    }

    @Override
    public void visitSqlExprProjection(SqlExprProjection node) {
        SqlExpr expr = node.getExpr();
        // Plan 2307 V6d: exprMeta 未解析时此处原先无判空即调 getColumnNames()，
        // 抛裸 NullPointerException（无错误码、无定位）。实测触发路径：
        //   with recursive t as (select 1 union all select 2 from t) select 1 from t
        // 即 CTE 体内的投影没有 resolvedExprMeta。
        // 下面 :149 已经在判 getColumnNames() 是否为 null，说明作者预期过"取不到列名"，
        // 但漏了 exprMeta 本身为 null 的情形。改为带错误码的显式失败。
        ISqlExprMeta exprMeta = expr != null ? expr.getResolvedExprMeta() : null;
        if (exprMeta == null) {
            throw new NopException(ERR_EQL_UNSUPPORTED_PROJECTION_TYPE)
                    .source(node)
                    .param(ARG_AST_NODE, String.valueOf(node.getASTKind()));
        }

        SqlAlias alias = node.getAlias();

        String owner = expr.getResolvedOwner();
        if (owner != null && owner.equals(ownerShouldBeIgnored))
            owner = null;

        List<String> colNames = exprMeta.getColumnNames();
        if (colNames == null) {
            super.visitSqlExprProjection(node);
        } else {
            for (int i = 0, n = exprMeta.getColumnCount(); i < n; i++) {
                if (i != 0) {
                    print(',');
                }
                printCol(owner, colNames.get(i));
                printAlias(alias, i, n);
            }
        }
    }

    void printAlias(SqlAlias alias, int index, int count) {
        if (alias == null)
            return;

        String name = EqlHelper.getAlias(alias.getAlias(), index, count);
        print(" as ");
        if (!alias.isGenerated()) {
            name = dialect.escapeSQLName(name);
        }
        print(name);
        print(' ');
    }

    void printCol(String owner, String colName) {
        // 如果是sqlText
        if (colName.startsWith("@")) {
            String sqlText = colName.substring(1);
            sqlText = EqlHelper.replaceOwner(owner, sqlText);
            print(sqlText);
        } else {
            if (owner != null) {
                print(owner);
                print('.');
            }
            print(colName);
        }
    }

    @Override
    protected void printWhere(SqlQuerySelect node) {
        List<SqlSingleTableSource> tables = node.getFrom().getEntitySources();
        if (filteredSources.containsAll(tables) || tables.isEmpty()) {
            if (node.getWhere() != null) {
                println();
                visitSqlWhere(node.getWhere());
            }
        } else {
            println();
            if (node.getWhere() == null || node.getWhere().getExpr() == null) {
                if (enableFilter)
                    print("where 1=1 ");
            } else {
                visitSqlWhere((node.getWhere()));
            }

            if (enableFilter) {
                for (SqlSingleTableSource table : tables) {
                    if (filteredSources.contains(table))
                        continue;
                    addFilter(table);
                }
            }
        }
    }

    @Override
    public void visitSqlSingleTableSource(SqlSingleTableSource node) {
        visitSqlTableName(node.getTableName());

        appendPropJoins(node);
    }

    /**
     * WI20 (W2/T1): the SQL channel renders the TUMBLE pseudo-table function
     * verbatim — the generated text is deliberately NOT valid RDBMS SQL (the
     * windowed form is stream-only, sql-subset-and-semantics §4c). Rendering it
     * loudly (rather than dropping the table source like the visitor default)
     * makes any attempt to run the text on a real RDBMS fail fast at parse time
     * instead of silently querying the wrong rows.
     */
    @Override
    public void visitSqlTumbleTableSource(io.nop.orm.eql.ast.SqlTumbleTableSource node) {
        print("TUMBLE(");
        print(node.getTableName());
        print(".");
        print(node.getTimeColumn());
        print(", INTERVAL ");
        visit(node.getInterval().getExpr());
        print(" ");
        print(node.getInterval().getIntervalUnit().name());
        print(")");
        printAlias(node.getAlias());
    }

    void appendPropJoins(SqlSingleTableSource node) {
        if (node.getPropJoins() != null) {
            for (SqlPropJoin propJoin : node.getPropJoins().values()) {
                if (propJoin.isExplicit())
                    continue;

                println();
                incIndent();
                sb.append(' ');
                sb.append(propJoin.getJoinType().getText());
                sb.append(' ');
                visitSqlTableName(propJoin.getRight().getTableName());
                sb.append("on ");
                visit(propJoin.getCondition());

                addFilter(propJoin.getLeft());
                addFilter(propJoin.getRight());

                decIndent();
                appendPropJoins(propJoin.getRight());
            }
        }
    }

    /**
     * Plan 2308 D5：ORDER BY 的方言化输出。
     *
     * <p>覆写 {@link AstToEqlGenerator#visitSqlOrderBy}（而非只覆写 item）的原因：
     * 基类用 {@code printList(items, ",")} 做列表级拼接，而 CASE 兄弟键必须作为
     * <b>紧邻其排序项的前置同级键</b>插入；只覆写 item 无法在逗号之间插键。
     *
     * <p>D1 算法：{@code supportNullsFirst} 为 true → 原生输出 NULLS 子句；
     * 否则对可安全复制的表达式输出 CASE 改写。{@code defaultNullsFirst} 全程不读
     * （单个布尔无法表达各方言真实空序模型，见 design §3.8）。
     */
    @Override
    public void visitSqlOrderBy(SqlOrderBy node) {
        print(" order by ");
        beginBlock();
        List<SqlOrderByItem> items = node.getItems();
        if (items != null) {
            for (int i = 0, n = items.size(); i < n; i++) {
                if (i != 0) {
                    print(",");
                    if (isPretty())
                        println();
                }
                printOrderByItemForSql(items.get(i));
            }
        }
        endBlock();
    }

    private void printOrderByItemForSql(SqlOrderByItem item) {
        Boolean nullsFirst = item.getNullsFirst();

        // 未请求空序：与 2307 基线逐字一致，不做任何额外处理
        if (nullsFirst == null) {
            visit(item);
            return;
        }

        // 原生支持：直接输出（基类已能按三态打印 nulls first/last）
        if (dialect.isSupportNullsFirst()) {
            visit(item);
            return;
        }

        // D4：窗口上下文无法附加额外排序键，改写结构性不适用 -> 显式失败
        if (inWindowContext(item)) {
            throw new NopException(ERR_EQL_NULLS_ORDER_NOT_SUPPORTED)
                    .source(item)
                    .param(ARG_REASON, "window-not-supported")
                    .param(ARG_DIALECT, dialectName());
        }

        // D2：改写要复制排序表达式，只允许纯列引用
        SqlExpr expr = item.getExpr();
        if (!(expr instanceof SqlColumnName)) {
            throw new NopException(ERR_EQL_NULLS_ORDER_NOT_SUPPORTED)
                    .source(item)
                    .param(ARG_REASON, "expression-not-duplicable")
                    .param(ARG_DIALECT, dialectName());
        }

        // D3：DISTINCT 查询在改写路径上一律 fail-fast。
        // 实测 PostgreSQL 16：`select distinct v from t order by (case when v is null then 0 else 1 end), v`
        // 即便 v 已在投影列表中，仍报 `for SELECT DISTINCT, ORDER BY expressions must appear in select list`
        // ——因为注入的 CASE 是一个**新的** ORDER BY 表达式，不在 select list 里。
        // 故「排序列已投影」只是必要条件，不足以让改写合法；只能整体拒绝。
        if (inDistinctSelect(item)) {
            throw new NopException(ERR_EQL_NULLS_ORDER_NOT_SUPPORTED)
                    .source(item)
                    .param(ARG_REASON, "distinct-not-supported")
                    .param(ARG_DIALECT, dialectName());
        }

        // D5：CASE 键作为前置同级键（紧邻其排序项），而不是整个列表的表头。
        // 语义：先按「是否为 NULL」分组，再按原列排序。
        // CASE 键的方向与原方向相反（先分组再排），故 ASC 原向 -> CASE 用 DESC。
        boolean asc = item.getAsc();
        print("case when ");
        visit(expr);
        print(" is null then ");
        print(nullsFirst == asc ? "1" : "0");
        print(" else ");
        print(nullsFirst == asc ? "0" : "1");
        print(" end");
        print(asc ? " desc" : " asc");
        print(", ");
        visit(expr);
        print(asc ? " asc " : " desc ");
    }

    /** D4：ORDER BY 项是否位于 OVER(...) 或命名窗口 {@code WINDOW w AS (...)} 内部 */
    private boolean inWindowContext(SqlOrderByItem item) {
        for (EqlASTNode p = item.getASTParent(); p != null; p = p.getASTParent()) {
            if (p instanceof SqlWindowExpr || p instanceof SqlWindowDecl)
                return true;
        }
        return false;
    }

    /** D3：该排序项是否位于 {@code SELECT DISTINCT} 查询内（改写路径此时结构上不合法） */
    private boolean inDistinctSelect(SqlOrderByItem item) {
        SqlQuerySelect select = enclosingSelect(item);
        return select != null && select.getDistinct();
    }

    private SqlQuerySelect enclosingSelect(SqlOrderByItem item) {
        for (EqlASTNode p = item.getASTParent(); p != null; p = p.getASTParent()) {
            if (p instanceof SqlQuerySelect)
                return (SqlQuerySelect) p;
        }
        return null;
    }

    private String dialectName() {
        return dialect == null ? "unknown" : dialect.getClass().getSimpleName();
    }

    @Override
    protected void printJoin(SqlJoinTableSource node) {
        // Plan 2307 V2b: CROSS JOIN 按定义不带 ON 子句，租户/逻辑删除等过滤条件必须落到 WHERE。
        // 若沿用普通 join 的 addJoinFilter，产物会是 `cross join T on (...)` —— 非法 SQL
        // （H2/PostgreSQL/MySQL 均拒绝），使 CROSS JOIN 无法端到端执行。
        // 这里刻意不调用 addJoinFilter：两侧表源因而仍不在 filteredSources 中，
        // 由 printWhere 统一收集进 WHERE 子句（语义等价：笛卡尔积再过滤 = 过滤后的笛卡尔积）。
        if (node.getJoinType() == SqlJoinType.CROSS_JOIN) {
            // 不变式：CROSS_JOIN 只能由 grammar 的 crossJoin_（无 ON）或
            // SqlJoinTableSource#normalize()（要求 condition==null）产生。
            // 若将来出现带条件的 CROSS_JOIN，`cross join ... on ...` 是非法 SQL，
            // 必须显式失败而不是静默丢弃条件。
            if (node.getCondition() != null) {
                throw new NopException(ERR_EQL_CROSS_JOIN_NOT_ALLOW_CONDITION).source(node);
            }
            return;
        }

        if (node.getCondition() != null) {
            print(" on ");
            visit(node.getCondition());
        } else {
            if (enableFilter)
                print(" on 1=1 ");
        }

        if (enableFilter) {
            if (node.getLeft() instanceof SqlSingleTableSource) {
                addJoinFilter((SqlSingleTableSource) node.getLeft());
            }
            if (node.getRight() instanceof SqlSingleTableSource) {
                addJoinFilter((SqlSingleTableSource) node.getRight());
            }
        }
    }

    private void addJoinFilter(SqlSingleTableSource source) {
        if (source.isMainSource())
            return;
        addFilter(source);
    }

    private void addFilter(SqlSingleTableSource source) {
        if (enableFilter && filteredSources.add(source)) {
            ISqlTableMeta tableMeta = (ISqlTableMeta) source.getResolvedTableMeta();
            sb.addFilterMarker(SyntaxMarker.TAG_AND, tableMeta.getEntityName(), source.getAliasName());
        }
    }

    @Override
    public void visitSqlTableName(SqlTableName node) {
        ISqlSelectionMeta table = node.getResolvedTableMeta();
        String alias = getTableAlias(node);
        if (table instanceof EntityTableMeta) {
            IEntityModel entityModel = ((EntityTableMeta) table).getEntityModel();
            sb.markTable(normalizeTableName(entityModel.getTableName()), alias, entityModel.getName(), dialect.isUseAsInFrom());
        } else {
            sb.markTable(dialect.escapeSQLName(node.getName()), alias, null, dialect.isUseAsInFrom());
        }
    }

    String getTableAlias(SqlTableName node) {
        if (node.getASTParent().getASTKind() == EqlASTKind.SqlSingleTableSource) {
            SqlSingleTableSource source = (SqlSingleTableSource) node.getASTParent();
            SqlAlias alias = source.getAlias();
            if (alias == null)
                return null;
            return alias.getAlias();
        }
        return null;
    }

    @Override
    public void visitSqlColumnName(SqlColumnName node) {
        if (node.getProjection() != null) {
            super.visitSqlColumnName(node);
            return;
        }

        ISqlExprMeta exprMeta = node.getResolvedExprMeta();
        String owner = node.getResolvedOwner();
        if (owner != null && owner.equals(ownerShouldBeIgnored))
            owner = resolveIgnoredOwner(node);

        List<String> colNames = exprMeta.getColumnNames();
        if (colNames == null) {
            super.visitSqlColumnName(node);
            return;
        }
        if (colNames.size() == 1) {
            printCol(owner, colNames.get(0));
        } else {
            print('(');
            for (int i = 0, n = exprMeta.getColumnCount(); i < n; i++) {
                if (i != 0) {
                    print(',');
                }
                printCol(owner, colNames.get(i));
            }
            print(')');
        }
    }

    /**
     * UPDATE/DELETE语句不输出目标表别名，因此目标表的列不能按别名限定。顶层where/set中的列直接丢弃前缀；
     * 但相关子查询内引用目标表别名时不能按名字丢弃前缀，否则会按内层作用域解析到子查询表的同名列，
     * 这里改用目标表的物理表名来限定。如果别名被子查询中的同名别名遮蔽，则保留别名前缀。
     */
    private String resolveIgnoredOwner(SqlColumnName node) {
        EqlASTNode stm = findDmlStatement(node);
        if (stm instanceof ISqlTableSourceSupport) {
            SqlTableSource targetSource = ((ISqlTableSourceSupport) stm).getResolvedTableSource();
            if (targetSource != null) {
                if (targetSource != node.getTableSource()) {
                    // 子查询中定义了同名别名，该列实际引用的是子查询表，别名已在SQL中声明，保留前缀
                    return node.getResolvedOwner();
                }

                if (isInSubQuery(node)) {
                    // 相关子查询内引用UPDATE/DELETE目标表，改用物理表名限定
                    ISqlSelectionMeta tableMeta = targetSource.getResolvedTableMeta();
                    if (tableMeta instanceof EntityTableMeta)
                        return normalizeTableName(((EntityTableMeta) tableMeta).getEntityModel().getTableName());
                }
            }
        }
        return null;
    }

    private EqlASTNode findDmlStatement(EqlASTNode node) {
        for (EqlASTNode n = node; n != null; n = n.getASTParent()) {
            EqlASTKind kind = n.getASTKind();
            if (kind == EqlASTKind.SqlUpdate || kind == EqlASTKind.SqlDelete || kind == EqlASTKind.SqlInsert)
                return n;
        }
        return null;
    }

    private boolean isInSubQuery(EqlASTNode node) {
        for (EqlASTNode n = node.getASTParent(); n != null; n = n.getASTParent()) {
            switch (n.getASTKind()) {
                case SqlQuerySelect:
                case SqlUnionSelect:
                case SqlSelectWithCte:
                    return true;
                case SqlUpdate:
                case SqlDelete:
                case SqlInsert:
                    return false;
                default:
                    break;
            }
        }
        return false;
    }

    @Override
    public void visitSqlStringLiteral(SqlStringLiteral node) {
        print(' ');
        print(dialect.getStringLiteral(node.getValue()));
        print(' ');
    }

    @Override
    public void visitSqlBooleanLiteral(SqlBooleanLiteral node) {
        print(dialect.getBooleanValueLiteral(node.getValue()));
    }


    @Override
    public void visitSqlDateTimeLiteral(SqlDateTimeLiteral node) {
        print(' ');
        switch (node.getType()) {
            case DATE: {
                LocalDate date = ConvertHelper.toLocalDate(node.getValue(), err -> newError(err, node));
                print(dialect.getDateLiteral(date));
                break;
            }
            case TIME: {
                LocalTime time = ConvertHelper.toLocalTime(node.getValue(), err -> newError(err, node));
                print(dialect.getTimeLiteral(time));
                break;
            }
            case TIMESTAMP: {
                Timestamp timestamp = ConvertHelper.toTimestamp(node.getValue(), err -> newError(err, node));
                print(dialect.getTimestampLiteral(timestamp));
                break;
            }
        }
        print(' ');
    }

    private NopException newError(ErrorCode errorCode, EqlASTNode node) {
        return new NopException(errorCode).param(ARG_AST_NODE, node);
    }

    @Override
    public void visitSqlRegularFunction(SqlRegularFunction node) {
        ISQLFunction func = node.getResolvedFunction();
        SqlExprList funcExpr = func.buildFunctionExpr(node.getLocation(), node.getArgs(), dialect);
        this.printExprList(funcExpr);
    }

    @Override
    public void visitSqlCastExpr(SqlCastExpr node) {
        SqlTypeExpr expr = node.getDataType();
        StdSqlType sqlType = StdSqlType.fromStdName(expr.getName());
        if (sqlType == null) {
            super.visitSqlCastExpr(node);
            return;
        }

        print("cast(");
        visit(node.getExpr());
        print(" as ");
        int precision = expr.getPrecision() <= 0 ? -1 : expr.getPrecision();
        int scale = expr.getScale();
        if (precision < 0 && sqlType == StdSqlType.VARCHAR) {
            print("VARCHAR");
        } else {
            SQLDataType dataType = dialect.stdToNativeSqlType(sqlType, precision, scale);
            print(dataType.toString());
        }
        print(")");
    }

    @Override
    public void visitSqlLikeExpr(SqlLikeExpr node) {
        if (!node.getIgnoreCase()) {
            super.visitSqlLikeExpr(node);
            return;
        }

        ISQLFunction func = dialect.getFunction(SqlOperator.ILIKE.getText());
        if (func != null) {
            SqlExprList expr = func.buildFunctionExpr(node.getLocation(), Arrays.asList(node.getExpr(), node.getValue()), dialect);
            if (node.getNot()) {
                print("not (");
                printExprList(expr);
                print(")");
            } else {
                printExprList(expr);
            }
        } else {
            // 静默降级为大小写敏感的LIKE会改变查询语义，必须显式报错
            throw newError(ERR_EQL_NOT_SUPPORT_ILIKE, node).param(ARG_FUNC_NAME, SqlOperator.ILIKE.getText());
        }
    }

    @Override
    protected String getIntercectKeyword() {
        String keyword = dialect.getIntersectKeyword();
        if (!StringHelper.isEmpty(keyword))
            return keyword;

        return super.getIntercectKeyword();
    }

    @Override
    protected String getExceptKeyword() {
        String keyword = dialect.getExceptKeyword();
        if (!StringHelper.isEmpty(keyword))
            return keyword;
        return super.getExceptKeyword();
    }

    @Override
    protected void printCompareRange(SqlCompareRange compareRange) {
        if (compareRange == SqlCompareRange.ALL) {
            print(SqlCompareRange.ALL.name());
        } else {
            if (dialect.isSupportSomeSubQuery()) {
                print("SOME");
            } else {
                print(SqlCompareRange.ANY.name());
            }
        }
    }

    @Override
    protected void appendWithColumns(SqlCteStatement stm) {
        if (stm.getRecursive() && stm.getStatement() instanceof SqlUnionSelect) {
            SqlUnionSelect union = (SqlUnionSelect) stm.getStatement();
            List<SqlProjection> projections = union.getLeft().getProjections();

            // H2数据库要求必须有columns段
            print('(');
            int index = 0;
            for (SqlProjection projection : projections) {
                if (index == 0) {
                    index++;
                } else {
                    print(',');
                }

                // Plan 2307 V6: 投影类型与 exprMeta 都必须显式校验。
                // 修复前两处是无条件强转 / 无判空解引用，实测
                // `with recursive t as (select 1 union all select 2 from t) select 1 from t`
                // 抛裸 NullPointerException（exprMeta 为 null 却直接调 getColumnNames()），
                // 无错误码、无定位——EQL 的递归 CTE 因此整体不可用。
                // 改为带错误码的显式失败，不 catch 后继续。
                if (!(projection instanceof SqlExprProjection)) {
                    throw new NopException(ERR_EQL_CTE_COLUMN_NAME_UNRESOLVED)
                            .source(projection)
                            .param(ARG_AST_NODE, String.valueOf(projection.getASTKind()));
                }
                SqlExprProjection node = (SqlExprProjection) projection;
                SqlExpr expr = node.getExpr();
                ISqlExprMeta exprMeta = expr != null ? expr.getResolvedExprMeta() : null;

                SqlAlias alias = node.getAlias();
                if (alias != null) {
                    print(alias.getAlias());
                } else {
                    if (exprMeta == null) {
                        throw new NopException(ERR_EQL_CTE_COLUMN_NAME_UNRESOLVED)
                                .source(node)
                                .param(ARG_AST_NODE, String.valueOf(node.getASTKind()));
                    }
                    List<String> colNames = exprMeta.getColumnNames();
                    for (int i = 0, n = exprMeta.getColumnCount(); i < n; i++) {
                        if (i != 0) {
                            print(',');
                        }
                        print(EqlHelper.getAlias(colNames.get(i), i, n));
                    }
                }
            }
            print(')');
        }
    }
}