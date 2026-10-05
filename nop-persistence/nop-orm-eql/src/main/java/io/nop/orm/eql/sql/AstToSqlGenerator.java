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
import io.nop.commons.text.marker.Markers;
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
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlOrderBy;
import io.nop.orm.eql.ast.SqlOrderByItem;
import io.nop.orm.eql.ast.SqlParameterMarker;
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
import io.nop.orm.eql.ast.SqlWindowDecl;
import io.nop.orm.eql.ast.SqlWindowExpr;
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
import java.util.Map;
import java.util.Set;

import static io.nop.orm.eql.OrmEqlConstants.DIALECT_KEYWORD_NONE;
import static io.nop.orm.eql.OrmEqlConstants.FEATURE_EXCEPT_KEYWORD;
import static io.nop.orm.eql.OrmEqlConstants.FEATURE_INTERSECT_KEYWORD;
import static io.nop.orm.eql.OrmEqlConstants.FEATURE_SUPPORT_LATERAL;
import static io.nop.orm.eql.OrmEqlErrors.ARG_AST_NODE;
import static io.nop.orm.eql.OrmEqlErrors.ARG_DIALECT;
import static io.nop.orm.eql.OrmEqlErrors.ARG_REASON;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_CROSS_JOIN_NOT_ALLOW_CONDITION;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_CTE_COLUMN_NAME_UNRESOLVED;
import static io.nop.orm.eql.OrmEqlErrors.ARG_FUNC_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_FEATURE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_ILIKE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NULLS_ORDER_NOT_SUPPORTED;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNSUPPORTED_PROJECTION_TYPE;

public class AstToSqlGenerator extends AstToEqlGenerator {

    private final IDialect dialect;

    private final boolean enableFilter;

    private final Set<SqlSingleTableSource> filteredSources = CollectionHelper.newIdentityHashSet();

    /**
     * plan 2320：SqlParameterMarker AST节点 → 该节点输出的?在builder全部ValueMarker中的序数。
     * 序数即执行期paramValues（CompiledSql.buildParams产物）的下标（changeMarkerValues按
     * ValueMarker顺序1:1填值）。IdentityHashMap按AST节点身份索引；子生成器（分页包装、
     * select-xx无from分支）共享同一map，保证序数在共享buf上全局连续。
     */
    private Map<SqlParameterMarker, Integer> paramValueOrdinals;

    public AstToSqlGenerator(IDialect dialect, boolean enableFilter, SQL.SqlBuilder sb) {
        super(sb);
        this.dialect = dialect;
        this.enableFilter = enableFilter;
    }

    public AstToSqlGenerator(IDialect dialect, boolean enableFilter) {
        this(dialect, enableFilter, SQL.begin());
    }

    /**
     * 分页包装等子生成器与主生成器共享参数序数表（buf共享，序数连续）。
     */
    AstToSqlGenerator shareParamValueOrdinals(Map<SqlParameterMarker, Integer> shared) {
        this.paramValueOrdinals = shared;
        return this;
    }

    Map<SqlParameterMarker, Integer> paramValueOrdinals() {
        if (paramValueOrdinals == null)
            paramValueOrdinals = new java.util.IdentityHashMap<>();
        return paramValueOrdinals;
    }

    @Override
    public void visitSqlParameterMarker(SqlParameterMarker node) {
        // 记录该参数节点对应ValueMarker的序数（=当前已有的ValueMarker个数），供
        // ShardMarkerInjector把"参数序号"作为静态信息写入TABLE marker（缓存安全约束）
        int ordinal = countValueMarkers();
        super.visitSqlParameterMarker(node);
        paramValueOrdinals().put(node, ordinal);
    }

    /**
     * 当前builder中ValueMarker（即运行期参数占位?）的个数。
     */
    private int countValueMarkers() {
        List<Marker> markers = sb.getMarkers();
        if (markers == null)
            return 0;
        int count = 0;
        for (int i = 0, n = markers.size(); i < n; i++) {
            if (markers.get(i) instanceof Markers.ValueMarker)
                count++;
        }
        return count;
    }

    /**
     * 取参数节点在执行期paramValues中的序号。仅接受本生成器（含子生成器）输出过的节点。
     */
    public int getParamValueOrdinal(SqlParameterMarker node) {
        Integer ordinal = paramValueOrdinals == null ? null : paramValueOrdinals.get(node);
        if (ordinal == null)
            throw new IllegalArgumentException(
                    "sql param marker was not generated by this compiler: " + node.getASTKind());
        return ordinal;
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
            // plan 2320：子生成器与主生成器共享buf与参数序数表，保证paramValueOrdinal
            // 在同一buf上全局连续（分页包装产生的?全部来自内部select）
            AstToSqlGenerator gen = new AstToSqlGenerator(dialect, enableFilter, buf)
                    .shareParamValueOrdinals(paramValueOrdinals());
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
     * 【merge 裁决记录 / Plan 2308 supersede】本位置原为 ours WI7 Phase 2 的
     * {@code visitSqlOrderByItem} 三分门（null=委托 super / support=打印 / 否则按
     * {@code DOCUMENTED_DIRECTIONAL_NULL_ORDER} 围栏做"缺省一致省略、否则编译期报错"，
     * 裁定 ai-dev/design/eql-cross-db-compat/2026-10-04-wi7-phase0-adjudications.md §三）。
     *
     * <p>master 侧 Plan 2308/2309 对同一功能落地了**另一套发射策略**（见
     * {@link #visitSqlOrderBy(SqlOrderBy)}：support 位 gate + {@link NullsOrderRewriter}
     * CASE 改写 + D2/D3/D4 结构不可改写处 fail-fast），并在其设计文档（§3.8）中
     * 明确否决了 defaultNullsFirst 省略优化（单布尔无法表达各方言真实空序模型；
     * DuckDB ASC/DESC 均不翻转缺省，省略优化对该库不安全）。两策略对同一输入
     * （如 mysql {@code asc nulls last}）行为互斥（报错 vs CASE 改写），无法机械并集。
     *
     * <p>本合并采纳 master 发射策略，依据（四点，均为 ours 侧自身框架内成立）：
     * <ol>
     * <li>ours WI7 裁定文档"被拒备选(落地)"自设的翻案条件——"仅在能给出逐方言无损
     * lowering 矩阵时成立"——已被 master 满足：Plan 2308 Phase 0 探针
     * {@code ai-dev/tools/probe-nulls-order-matrix.sh} 在真实容器（postgres:16/mysql:8.0）
     * 实测四方向组合行序，CASE 四组合方向正确性由 {@code TestEqlNullsOrderCompile} 钉死；</li>
     * <li>master 对 db2 的 supportNullsFirst=true 一手修正（0ea57a097e，经用户指出）——
     * 本合并任务书明确要求按其口径调整 ours 测试；</li>
     * <li>bean 通道（nop-orm GenSqlHelper，Plan 2309 Phase 1）与本通道共用
     * NullsOrderRewriter——两通道必须同策略，否则同一语义两通道行为分裂；</li>
     * <li>机械合并事实：两实现并存时 master 的 visitSqlOrderBy 接管列表打印，
     * ours 门的报错/省略分支在编译管线中结构性不可达（仅 null/support 两分支被回调且恒等委托），
     * 即自动合并后的实际运行行为已是 master 策略；保留不可达死门只会制造双源真相。</li>
     * </ol>
     *
     * <p>ours WI7 的三分语义在 master 策略中的对应：分支 0（null 不补子句）=
     * printOrderByItemForSql 首分支（逐字一致，BC 保持）；分支 1（support 请求即打印）=
     * 其次分支；分支 2（不支持方言的显式请求不静默）由 CASE 无损改写（可安全复制的
     * 纯列引用）或 D2/D3/D4 响亮报错（结构不可改写）承接——"响亮失败优于静默赌注"原则
     * 保持，且对合法请求从"拒绝"升级为"正确模拟"。ours 原三分门全文与本记录见
     * 本文件 git 历史（merge 提交）与 ai-dev/logs 合并条目。
     *
     * <p>ours 侧同步退役物：{@code DOCUMENTED_DIRECTIONAL_NULL_ORDER} 围栏集、
     * {@code defaultNullsFirstForDirection} 推导（本注释即其原位置）；
     * {@code IDialect.isDefaultNullsFirst()} accessor 本身保留（配置事实登记，
     * Plan 2308 明示"全程不读"；TestDefaultNullsFirstFlag 继续钉其取值语义）。
     * ours 的 {@code TestEqlNullsFirstDialectGate} 已按新契约改写
     * （{@code TestEqlNullsFirstDialectGatePostMerge} 语义，同文件内注释标明各用例对应关系）。
     */

    /**
     * WI8（plan 27；裁定 ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md
     * §三/§四.1）：{@code IS [NOT] DISTINCT FROM} 的 SAFE_EQ 渲染三分支名称集合（不新增方言
     * 能力标志——名称集合先例 = 原 WI7 围栏集 DOCUMENTED_DIRECTIONAL_NULL_ORDER，已随
     * merge 裁决退役，见上方 merge 裁决记录；继承别名
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
        if (!(item.getExpr() instanceof SqlColumnName)) {
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
        // 改写算法本身在 NullsOrderRewriter（plan 2309 Phase 1），与 bean 通道共用同一份实现。
        SqlExpr expr = (SqlExpr) item.getExpr();
        NullsOrderRewriter.appendCaseRewrite(getSql(), sb -> visit(expr),
                item.getAsc(), nullsFirst);
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