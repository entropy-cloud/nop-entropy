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
import io.nop.orm.eql.ast.SqlBinaryExpr;
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
import io.nop.orm.eql.ast.SqlNotExpr;
import io.nop.orm.eql.ast.SqlOrderByItem;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlRegularFunction;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.ast.SqlSubqueryTableSource;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlTypeExpr;
import io.nop.orm.eql.ast.SqlUnionSelect;
import io.nop.orm.eql.compile.ISqlTableSourceSupport;
import io.nop.orm.eql.compile.SqlPropJoin;
import io.nop.orm.eql.enums.SqlCompareRange;
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

import static io.nop.orm.eql.OrmEqlConstants.DIALECT_KEYWORD_NONE;
import static io.nop.orm.eql.OrmEqlConstants.FEATURE_EXCEPT_KEYWORD;
import static io.nop.orm.eql.OrmEqlConstants.FEATURE_INTERSECT_KEYWORD;
import static io.nop.orm.eql.OrmEqlConstants.FEATURE_SUPPORT_LATERAL;
import static io.nop.orm.eql.OrmEqlErrors.ARG_AST_NODE;
import static io.nop.orm.eql.OrmEqlErrors.ARG_DIALECT;
import static io.nop.orm.eql.OrmEqlErrors.ARG_FUNC_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_FEATURE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_ILIKE;

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
        checkLockOptions(node);
        if (node.getLimit() != null) {
            SqlLimit limit = node.getLimit();
            SqlExprList exprs = dialect.getPaginationHandler().buildPageExpr(limit.getLimit(), limit.getOffset(),
                    new PrintSqlSelect(node));
            printExprList(exprs);
        } else {
            printSelect(node);
        }
    }

    /**
     * WI12 交付①（plan 38，裁定
     * ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §1.5）：
     * 锁选项矛盾输入在生成器入口响亮。解析层文法备选天然互斥（NOWAIT | SKIP LOCKED 单选，
     * 且必须跟在 for update 之后），矛盾组合仅程序化构造可达——不允许静默输出或静默忽略
     * （Minimum Rules #24）。校验放在 printForUpdate 的 forUpdate 守卫之前，
     * 覆盖 forUpdate=false 的矛盾组合。回显路径（AstToEqlGenerator）不加校验
     * （WI1 裁定回显保持硬编码不动；矛盾 AST 是编程错误，执行路径已响亮）。
     */
    void checkLockOptions(SqlQuerySelect node) {
        if (node.getNowait() && node.getSkipLocked())
            throw new IllegalArgumentException(
                    "eql lock options nowait and skipLocked are mutually exclusive");
        if (node.getNowait() && !node.getForUpdate())
            throw new IllegalArgumentException(
                    "eql lock option nowait requires forUpdate=true, but forUpdate=false");
        if (node.getSkipLocked() && !node.getForUpdate())
            throw new IllegalArgumentException(
                    "eql lock option skipLocked requires forUpdate=true, but forUpdate=false");
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

    /**
     * WI2 BUG-4: 执行路径的 limit 已由 {@link #visitSqlQuerySelect(SqlQuerySelect)} 经
     * {@code dialect.getPaginationHandler().buildPageExpr(...)} 消费（分页文本在外层
     * 追加），printSelect 内不再打印——否则经 printWrappedNode→printSelect 的分页内嵌
     * 渲染路径会与外层 LIMIT/OFFSET 文本重复。基类钩子仅服务回显路径（AstToEqlGenerator）。
     */
    @Override
    protected void printLimit(SqlQuerySelect node) {
        // limit 在 visitSqlQuerySelect 的分页分支消费，此处刻意不输出（非静默跳过：
        // 消费点在 buildPageExpr，行为由 TestEqlSelectAllLimitCompile 的单 LIMIT 断言钉死）
    }

    @Override
    public void visitSqlExprProjection(SqlExprProjection node) {
        SqlExpr expr = node.getExpr();
        ISqlExprMeta exprMeta = expr.getResolvedExprMeta();

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
     * WI12 交付⑤（plan 38，裁定
     * ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §4）：
     * {@code FROM (VALUES …)} 表源执行路径——方言能力门
     * （{@code isSupportValuesTableSource()}，不支持编译期响亮，feature=valuesTableSource，
     * 不静默产出目标库不支持的文本）+ 原生输出 {@code (values (…),(…)) alias (c1,c2)}。
     * 别名列清单缺省时不输出列清单（H2 缺省列名 C1..Cn）。UNION ALL 链 lowering 为
     * 被拒备选（裁定 §4：需 per-dialect dual 表体系，不无损），不落。
     */
    @Override
    public void visitSqlValuesTableSource(io.nop.orm.eql.ast.SqlValuesTableSource node) {
        if (!dialect.isSupportValuesTableSource()) {
            throw newError(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE, node)
                    .param(ARG_DIALECT, dialect.getName())
                    .param(ARG_FEATURE, "valuesTableSource");
        }
        print('(');
        print("values ");
        boolean first = true;
        for (io.nop.orm.eql.ast.SqlMultiValueExpr row : node.getRows()) {
            if (!first)
                print(", ");
            first = false;
            visit(row);
        }
        print(')');
        printAlias(node.getAlias());
        List<String> columns = node.getColumns();
        if (columns != null && !columns.isEmpty()) {
            print('(');
            print(StringHelper.join(columns, ","));
            print(')');
        }
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

    /**
     * WI7b Phase 1（plan 37；roadmap EC ③，BUG-5 的另一半）：执行 SQL 的 interval 字面量按方言
     * 模板 `sqlIntervalLiteral` 渲染。修复前所有方言都经基类裸输出 ` interval <expr> <UNIT>`，
     * 探针已证该形态在 H2 与 PG 上非法（`ai-dev/audits/eql-wi0/2026-10-04-h2-probe-transcript.txt`）。
     *
     * <p>裁定（`ai-dev/design/eql-cross-db-compat/2026-10-04-wi7b-phase0-adjudications.md` §1/§2）：
     * <ul>
     * <li>模板未配置 → 委托 super（既有裸输出逐字节不变，BC；deprecated 语义见 owner doc）；</li>
     * <li>已配置 → 命名 marker `{value}`/`{unit}` 纯文本拼接渲染（value 走真实渲染路径捕获），
     * 不做二次转义；</li>
     * <li>已配置方言上非字面量 value（参数 marker/列引用等）编译期显式报错（处置②）——回落裸输出
     * 会继续产出已知非法文本，正中"只打印不设门 = 净回归"；</li>
     * <li>TUMBLE 伪表函数渲染路径（{@link #visitSqlTumbleTableSource}）刻意逐字输出、不经模板——
     * 其 owner 是 nop-stream-sql roadmap WI20，本方法零触碰。</li>
     * </ul>
     */
    @Override
    public void visitSqlIntervalExpr(io.nop.orm.eql.ast.SqlIntervalExpr node) {
        String template = dialect.getSqlIntervalLiteral();
        if (StringHelper.isEmpty(template)) {
            super.visitSqlIntervalExpr(node);
            return;
        }

        if (!(node.getExpr() instanceof SqlNumberLiteral)) {
            throw newError(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE, node)
                    .param(ARG_DIALECT, dialect.getName())
                    .param(ARG_FEATURE, "intervalNonLiteralValue");
        }

        // value 经真实渲染路径捕获（接线证据：同一 visit 管线），再纯文本拼接进模板。
        // 捕获文本 trim 外层空白（pretty 打印会在 token 间加空格），内部空白保留。
        SQL.SqlBuilder buf = SQL.begin();
        new AstToSqlGenerator(dialect, enableFilter, buf).visit(node.getExpr());
        print(' ');
        print(renderIntervalTemplate(template, buf.toString().trim(), node.getIntervalUnit().name()));
    }

    private String renderIntervalTemplate(String template, String valueText, String unit) {
        StringBuilder sb = new StringBuilder(template.length() + valueText.length() + unit.length());
        int pos = 0;
        while (true) {
            int p1 = template.indexOf('{', pos);
            if (p1 < 0) {
                sb.append(template, pos, template.length());
                break;
            }
            int p2 = template.indexOf('}', p1);
            // 加载期（DialectImpl 构造器）已拒绝坏 marker，此处仅为防御性兜底
            if (p2 < 0) {
                sb.append(template, pos, template.length());
                break;
            }
            sb.append(template, pos, p1);
            String name = template.substring(p1 + 1, p2);
            if ("value".equals(name)) {
                sb.append(valueText);
            } else if ("unit".equals(name)) {
                sb.append(unit);
            }
            pos = p2 + 1;
        }
        return sb.toString();
    }

    /**
     * WI1 BUG-2: 执行路径按方言门控 LATERAL 子查询表源。修复前 lateral 关键字被静默
     * 丢弃（`lateral (sub)` 与 `(sub)` 输出字节相同），退化为不相关子查询——现在
     * `supportLateral=false`（含未配置的保守缺省）时编译期显式报错，`true` 时由基类
     * 无损打印。回显路径不受方言开关影响（走基类实现）。
     */
    @Override
    public void visitSqlSubqueryTableSource(SqlSubqueryTableSource node) {
        if (node.getLateral() && !dialect.isSupportLateral()) {
            throw newError(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE, node)
                    .param(ARG_DIALECT, dialect.getName())
                    .param(ARG_FEATURE, FEATURE_SUPPORT_LATERAL);
        }
        super.visitSqlSubqueryTableSource(node);
    }

    /**
     * WI1 BUG-3: 执行路径消费方言锁模板 `IDialect.getForUpdateSql`。修复前 EQL 路径
     * 硬编码输出 "for update"，在锁模板为空的方言（如 duckdb）上静默产出数据库不支持
     * 的 SQL。空模板时编译期显式报错；LockOption 暂传 null（AST 只有 boolean forUpdate，
     * NOWAIT/SKIP LOCKED 归 WI12）。回显路径（基类）保持不动。
     *
     * <p>WI12 交付①（plan 38，裁定 §1）：nowait=true 时锁文本改取
     * {@code <sqls>} 的 {@code <forUpdateNowait>} 配置（presence = 能力 + 文本二合一）；
     * 未配置方言编译期响亮 {@code ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE}
     * （feature=forUpdateNowait）——不允许静默降级为裸 for update。
     */
    @Override
    protected void printForUpdate(SqlQuerySelect node) {
        String lockSql;
        String feature;
        if (node.getNowait()) {
            lockSql = dialect.getForUpdateNowait();
            feature = "forUpdateNowait";
        } else if (node.getSkipLocked()) {
            // WI12 交付②（plan 38）：SKIP LOCKED 变体经 <forUpdateSkipLocked> 配置文本
            lockSql = dialect.getForUpdateSkipLocked();
            feature = "forUpdateSkipLocked";
        } else {
            lockSql = dialect.getForUpdateSql(null);
            feature = "forUpdate";
        }
        if (StringHelper.isEmpty(lockSql)) {
            throw newError(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE, node)
                    .param(ARG_DIALECT, dialect.getName())
                    .param(ARG_FEATURE, feature);
        }
        println();
        print(lockSql);
    }

    /**
     * WI7（roadmap EC ②/③/③b）：执行路径的 {@code NULLS FIRST/LAST} 方言门——替代继承自
     * {@code AstToEqlGenerator} 的无门打印（"只打印不设门"在 MySQL 等不支持子句的方言上
     * 就是净回归）。三分形态（裁定 = ai-dev/design/eql-cross-db-compat/
     * 2026-10-04-wi7-phase0-adjudications.md §三）：
     * <ol>
     * <li>{@code nullsFirst == null}（未指定）→ 委托 super，输出与本 override 引入前
     * 逐字节一致（BC 棘轮硬约束，缺省不主动补子句）；</li>
     * <li>{@code dialect.isSupportNullsFirst()}（原生接受子句，duckdb/h2/oracle/postgresql）
     * → 委托 super（继承行为 = 按请求打印 {@code nulls first}/{@code nulls last}）——请求即
     * 语义，无需查缺省序，方向相关的缺省差异在此分支内自然消解；</li>
     * <li>false 且显式请求 → 缺省序推导（仅一手文档已证方言集，见
     * {@link #defaultNullsFirstForDirection(boolean)}）：请求与该方向缺省序一致则省略子句
     * （行为等价、无损）；不一致或不可推导（dm/tdengine/db2 等无一手文档）编译期响亮报错，
     * 不静默丢请求。</li>
     * </ol>
     * 回显路径（基类）不做方言判断，保持不动（与 lateral/forUpdate 门只设在执行路径的先例一致）。
     */
    @Override
    public void visitSqlOrderByItem(SqlOrderByItem node) {
        Boolean nullsFirst = node.getNullsFirst();
        if (nullsFirst == null || dialect.isSupportNullsFirst()) {
            super.visitSqlOrderByItem(node);
            return;
        }

        Boolean defaultFirst = defaultNullsFirstForDirection(node.getAsc());
        if (defaultFirst == null || !defaultFirst.equals(nullsFirst)) {
            throw newError(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE, node)
                    .param(ARG_DIALECT, dialect.getName())
                    .param(ARG_FEATURE, "nullsFirst(" + (nullsFirst ? "first" : "last") + ")");
        }

        // 显式请求与缺省序一致 ⇒ 省略子句（行为等价），手打 expr + asc（不能委托 super：
        // 继承实现会为非 null 的 nullsFirst 打印子句）
        visit(node.getExpr());
        print(node.getAsc() ? " asc " : " desc ");
    }

    /**
     * 一手文档已证「缺省 NULL 序方向相对」的方言集（裁定文档 §三；不在集合内 = 缺省序不可
     * 推导 ⇒ 显式请求一律报错，红线 [未取证] 纪律——配置值是登记事实但不作等价性主张）：
     * <ul>
     * <li>mysql/mysql5.7/mariadb（继承 mysql 的已证行为）：MySQL 手册 "NULL values are
     * presented first if you do ORDER BY ... ASC and last if you do ORDER BY ... DESC"</li>
     * <li>mssql：NULL 视为最低值（ASC 缺省 FIRST / DESC 缺省 LAST）</li>
     * <li>h2：LOW/HIGH 方向相对（H2 文档 "considered as smaller than other values during
     * sorting"，h2.dialect.xml 既有引文；h2 supportNullsFirst=true 实际不可达，登记为完备）</li>
     * </ul>
     * dm/tdengine（XML 有配置但无一手文档）与 db2（未配置 + 文档不可得）均不在集合内：
     * 两方向显式请求一律报错，不得临时放行（裁定文档 §三 explicit 列名）。
     */
    private static final Set<String> DOCUMENTED_DIRECTIONAL_NULL_ORDER =
            Set.of("mysql", "mysql5.7", "mariadb", "mssql", "h2");

    /**
     * @return 该方向缺省序是否 nulls first；null = 不可推导（方言不在一手文档已证集）。
     *         ASC 方向 = {@code isDefaultNullsFirst()}；DESC 方向 = 镜像（nulls 最小 ⇒
     *         ASC 在前 DESC 在后，反之亦然）。
     */
    private Boolean defaultNullsFirstForDirection(boolean asc) {
        if (!DOCUMENTED_DIRECTIONAL_NULL_ORDER.contains(dialect.getName()))
            return null;
        boolean defaultFirst = dialect.isDefaultNullsFirst();
        return asc ? defaultFirst : !defaultFirst;
    }

    /**
     * WI8（plan 27；裁定 ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md
     * §三/§四.1）：{@code IS [NOT] DISTINCT FROM} 的 SAFE_EQ 渲染三分支名称集合（不新增方言
     * 能力标志——名称集合先例 = {@link #DOCUMENTED_DIRECTIONAL_NULL_ORDER}；继承别名
     * mysql5.7/mariadb/h2gis/postgis 显式列举）。db2/dm/tdengine/sqlite 无一手取证
     * 或无方言文件，均不在集合内 ⇒ 落报错分支（unverified 纪律，不静默放行）。
     */
    private static final Set<String> SAFE_EQ_MYSQL_FAMILY = Set.of("mysql", "mysql5.7", "mariadb");

    private static final Set<String> NATIVE_IS_DISTINCT_FROM =
            Set.of("postgresql", "postgis", "h2", "h2gis", "duckdb", "mssql");

    @Override
    public void visitSqlBinaryExpr(SqlBinaryExpr node) {
        if (node.getOperator() == SqlOperator.SAFE_EQ) {
            // 渲染三分支必选件：printOperator 全局无方言分支，不覆写则全方言输出 <=>（静默非法 SQL）
            printSafeEq(node, false);
            return;
        }
        super.visitSqlBinaryExpr(node);
    }

    @Override
    public void visitSqlNotExpr(SqlNotExpr node) {
        if (node.getExpr() instanceof SqlBinaryExpr
                && ((SqlBinaryExpr) node.getExpr()).getOperator() == SqlOperator.SAFE_EQ) {
            // IS DISTINCT FROM（SqlNotExpr(SAFE_EQ) 规范形态）按方向文本直出，
            // 禁止 not(a is not distinct from b) 包装（裁定 §四.1 分支 2）
            printSafeEq((SqlBinaryExpr) node.getExpr(), true);
            return;
        }
        super.visitSqlNotExpr(node);
    }

    /**
     * SAFE_EQ 渲染三分支（裁定 §四.1）：mysql 族 → {@code a <=> b} / {@code not (a <=> b)}
     * （{@code <=>} 恒二值，NOT 良定义）；native 集合 → {@code is [not] distinct from}
     * 方向文本直出；其余方言 → 响亮 ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE——无 transformer
     * 时 oracle 亦落此分支（Phase 3 默认注入 transformer 后由其展开，SAFE_EQ 不再到达生成器）。
     * 全仓不存在向不支持方言静默输出 {@code <=>} 的路径（Minimum Rules #24）。
     */
    private void printSafeEq(SqlBinaryExpr node, boolean negated) {
        String dialectName = dialect.getName();
        boolean mysqlFamily = dialectName != null && SAFE_EQ_MYSQL_FAMILY.contains(dialectName);
        if (mysqlFamily) {
            if (negated) {
                print("not (");
                printBinaryExpr(node.getLeft(), SqlOperator.SAFE_EQ, node.getRight());
                print(")");
            } else {
                printBinaryExpr(node.getLeft(), SqlOperator.SAFE_EQ, node.getRight());
            }
            return;
        }

        boolean nativeSupport = dialectName != null && NATIVE_IS_DISTINCT_FROM.contains(dialectName);
        if (nativeSupport) {
            printLeft(node.getLeft(), SqlOperator.IS);
            print(negated ? " is distinct from " : " is not distinct from ");
            visit(node.getRight());
            return;
        }

        throw newError(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE, node)
                .param(ARG_DIALECT, dialectName)
                .param(ARG_FEATURE, "is[not] distinct from");
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

    @Override
    protected void printJoin(SqlJoinTableSource node) {
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
        SqlExpr escape = node.getEscape();
        if (node.getIgnoreCase() && escape != null) {
            // WI1 BUG-1: ilike 经方言函数改写（如 regexp_like）后 ESCAPE 子句没有挂载点，
            // 静默丢弃会改变匹配语义，必须显式报错（裁定: ai-dev/design/eql-cross-db-compat/
            // 2026-10-03-wi1-class-a-adjudications.md §5）
            throw newError(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE, node)
                    .param(ARG_DIALECT, dialect.getName())
                    .param(ARG_FEATURE, "ilike+escape");
        }

        if (!node.getIgnoreCase()) {
            super.visitSqlLikeExpr(node);
            // WI1 BUG-1: escape 字段此前被静默丢弃。默认 escape 字符随方言分叉
            // （{Oracle,DB2}=NULL / {H2,PG}=backslash），必须显式输出用户指定的 escape。
            if (escape != null) {
                print(" escape");
                visit(escape);
            }
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
        if (StringHelper.isEmpty(keyword))
            return super.getIntercectKeyword();

        // WI3 版本门：哨兵 none = 方言声明无 INTERSECT 算子（MySQL 5.7 / MariaDB 10.2 落地
        // 配置）。编译期响亮报错，不静默输出目标库不支持的算子。空串经 xdef 绑定实测被
        // 规范化为 null、与未配置不可区分，故用显式哨兵（裁定文档 §二）
        if (DIALECT_KEYWORD_NONE.equals(keyword))
            throw new NopException(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE)
                    .param(ARG_DIALECT, dialect.getName())
                    .param(ARG_FEATURE, FEATURE_INTERSECT_KEYWORD);

        return keyword;
    }

    @Override
    protected String getExceptKeyword() {
        String keyword = dialect.getExceptKeyword();
        if (!StringHelper.isEmpty(keyword)) {
            // WI3 版本门：EXCEPT 复用既有 exceptKeyword 属性、走同一条哨兵门规则
            if (DIALECT_KEYWORD_NONE.equals(keyword))
                throw new NopException(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE)
                        .param(ARG_DIALECT, dialect.getName())
                        .param(ARG_FEATURE, FEATURE_EXCEPT_KEYWORD);
            return keyword;
        }
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

                SqlExprProjection node = (SqlExprProjection) projection;
                SqlExpr expr = node.getExpr();
                ISqlExprMeta exprMeta = expr.getResolvedExprMeta();

                SqlAlias alias = node.getAlias();
                if (alias != null) {
                    print(alias.getAlias());
                } else {
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