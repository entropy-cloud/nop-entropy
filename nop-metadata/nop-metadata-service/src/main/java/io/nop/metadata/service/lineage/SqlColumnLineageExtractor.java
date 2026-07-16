package io.nop.metadata.service.lineage;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlAggregateFunction;
import io.nop.orm.eql.ast.SqlAllProjection;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlFrom;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlSelect;
import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlStatement;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlUnionSelect;
import io.nop.orm.eql.parse.EqlASTParser;
import io.nop.metadata.core._NopMetadataCoreConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 列级 SQL 血缘解析器（架构基线 §2.6.1 列级 sql_parse，P2-5+ 裁定 D1/D3）：对 tableType=sql 的视图 sourceSql
 * 做纯语法解析，将 SELECT 输出列映射回其引用的源表源列，返回 {@link ColumnLineageCandidate} 列表。
 *
 * <p>解析器选型（D1）：复用平台 {@code nop-orm-eql} 的 {@link EqlASTParser}，调用 {@code parseFromText(text)}
 * 做纯语法 AST 解析（不绑定 ORM session），与表级 {@link SqlSourceTableExtractor}、
 * {@code SqlSelectFieldExtractor} 同一解析器、同一无 session 绑定模式。
 *
 * <p>列引用归属解析（D1 关键限制，仅用句法字段）：
 * <ul>
 *   <li>从 FROM/JOIN 的 {@link SqlSingleTableSource} 手建 {@code scopeName(alias 或表名) → simpleTableName} 映射。</li>
 *   <li>对 projection 表达式内每个 {@link SqlColumnName} 用 {@code getOwner().getName()} 查映射归属源表。</li>
 *   <li><b>不得依赖 resolution 字段</b>（{@code getTableSource()}/{@code getResolvedOwner()} 纯 parse 后为 null，会 NPE）。</li>
 *   <li>无别名列引用：仅当 FROM 仅一张源表时归属该唯一源表；多表一律不可归属（纯句法无法判断，进 unresolved）。</li>
 * </ul>
 *
 * <p>transformType 判定（D3，AST 节点类型匹配）：直接列引用（expr 为 {@link SqlColumnName}）→ direct；
 * 聚合检测优先 {@code expr instanceof SqlAggregateFunction} → aggregated；其他表达式/函数 → derived。
 * 表达式列 walk expr 子树收集所有 {@link SqlColumnName} 节点，一个表达式输出列引用 N 个源列产出 N 条边。
 *
 * <p>不可解析形态显式标记 unresolved（不静默跳过、不伪造）：CTE/子查询别名穿透、{@code SELECT *} 通配符、
 * 多表无限定符歧义。SQL 为空/不可解析/多语句/非 SELECT → 显式抛 {@link NopException}。
 *
 * <p>本解析器无状态、无目录依赖（纯文本→候选列表）。
 */
public class SqlColumnLineageExtractor {

    static final ErrorCode ERR_COL_LINEAGE_SQL_EMPTY =
            ErrorCode.define("metadata.col-lineage-sql-empty", "Source sql is empty", "sql");
    static final ErrorCode ERR_COL_LINEAGE_SQL_PARSE_FAILED =
            ErrorCode.define("metadata.col-lineage-sql-parse-failed", "Failed to parse source sql for column lineage", "sql");
    static final ErrorCode ERR_COL_LINEAGE_MULTI_STATEMENT =
            ErrorCode.define("metadata.col-lineage-multi-statement",
                    "Sql source must be a single SELECT statement, but got {count} statements", "count", "sql");
    static final ErrorCode ERR_COL_LINEAGE_NOT_SELECT =
            ErrorCode.define("metadata.col-lineage-not-select",
                    "Sql source must be a SELECT statement, but got {statementKind}", "statementKind", "sql");

    private final EqlASTParser parser = new EqlASTParser();

    /**
     * 解析 SQL 文本，产出列级血缘候选列表。
     *
     * @param sql SQL 文本（tableType=sql 的视图定义 sourceSql）
     * @return 列级边候选列表（可解析的 + 不可解析的均保留，BizModel 层匹配目录）
     * @throws NopException 当 SQL 为空、不可解析、多语句、非 SELECT 时（不静默返回空列表）
     */
    public List<ColumnLineageCandidate> extract(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new NopException(ERR_COL_LINEAGE_SQL_EMPTY).param("sql", sql);
        }

        SqlProgram program;
        try {
            program = parser.parseFromText(null, sql);
        } catch (Exception e) {
            throw new NopException(ERR_COL_LINEAGE_SQL_PARSE_FAILED).param("sql", sql).cause(e);
        }
        if (program == null || program.getStatements() == null || program.getStatements().isEmpty()) {
            throw new NopException(ERR_COL_LINEAGE_SQL_PARSE_FAILED).param("sql", sql);
        }

        List<SqlStatement> statements = program.getStatements();
        if (statements.size() != 1) {
            throw new NopException(ERR_COL_LINEAGE_MULTI_STATEMENT)
                    .param("count", statements.size()).param("sql", sql);
        }

        SqlStatement stmt = statements.get(0);
        if (stmt.getStatementKind() != SqlStatementKind.SELECT) {
            throw new NopException(ERR_COL_LINEAGE_NOT_SELECT)
                    .param("statementKind", String.valueOf(stmt.getStatementKind())).param("sql", sql);
        }

        // 解析出含 FROM 子句的 SqlQuerySelect（钻入 CTE / UNION firstSelect）
        SqlQuerySelect query = resolveQuerySelect(stmt);
        if (query == null) {
            // getStatementKind()==SELECT 但非已知子类——不可达，显式失败而非静默
            throw new NopException(ERR_COL_LINEAGE_SQL_PARSE_FAILED)
                    .param("sql", sql)
                    .cause(new IllegalStateException("unhandled SELECT statement class: " + stmt.getClass().getName()));
        }

        // 构建 scopeName(lower) → simpleTableName 映射 + 源表计数
        AliasTableMap aliasMap = buildAliasTableMap(query.getFrom());

        List<SqlProjection> projections = query.getProjections();
        // 平台对裸 `SELECT *` 会静默丢弃通配符返回空 projections；此处显式失败（不静默返回空候选列表，
        // 因合法显式列 SELECT 必有 ≥1 个 projection，空列表唯一现实成因是被丢弃的裸 *）。
        if (projections == null || projections.isEmpty()) {
            throw new NopException(ERR_COL_LINEAGE_SQL_PARSE_FAILED)
                    .param("sql", sql)
                    .cause(new IllegalStateException("empty projections (bare wildcard '*' cannot be resolved)"));
        }

        List<ColumnLineageCandidate> candidates = new ArrayList<>();
        int exprIndex = 0;
        for (SqlProjection proj : projections) {
            if (proj instanceof SqlAllProjection) {
                // 通配符 t.* 纯 AST 无法展开 → 显式 unresolved（不静默跳过、不伪造）
                candidates.add(ColumnLineageCandidate.unresolvable("*", null, "wildcard-projection"));
                continue;
            }
            if (!(proj instanceof SqlExprProjection)) {
                // 未知 projection 子类——显式失败而非静默跳过
                throw new NopException(ERR_COL_LINEAGE_SQL_PARSE_FAILED)
                        .param("sql", sql)
                        .cause(new IllegalStateException("unhandled projection class: " + proj.getClass().getName()));
            }
            SqlExprProjection exprProj = (SqlExprProjection) proj;
            String targetColumn = resolveTargetColumn(exprProj, exprIndex);
            String transformType = resolveTransformType(exprProj.getExpr());
            if (targetColumn.startsWith("<expr_")) {
                exprIndex++;
            }
            // walk expr 子树收集所有 SqlColumnName，各列按归属产出候选
            List<SqlColumnName> cols = new ArrayList<>();
            collectColumnRefs(exprProj.getExpr(), cols);
            if (cols.isEmpty()) {
                // 常量列（如 SELECT 1 AS x）无源列引用 → 无血缘边，语义正确（非静默跳过）
                continue;
            }
            for (SqlColumnName col : cols) {
                candidates.add(attribute(col, targetColumn, transformType, aliasMap));
            }
        }
        return candidates;
    }

    /**
     * 从顶层 SELECT 语句取含 FROM 子句的 {@link SqlQuerySelect}。
     * {@link SqlSelectWithCte} 钻入 {@code getSelect()}；{@link SqlUnionSelect} 取 firstSelect
     * （UNION 列级血缘首版仅追踪 firstSelect 分支，与字段名解析口径一致）。
     */
    private SqlQuerySelect resolveQuerySelect(SqlStatement stmt) {
        SqlSelect select;
        if (stmt instanceof SqlSelectWithCte) {
            SqlSelect inner = ((SqlSelectWithCte) stmt).getSelect();
            if (inner == null) {
                return null;
            }
            select = inner;
        } else if (stmt instanceof SqlSelect) {
            select = (SqlSelect) stmt;
        } else {
            return null;
        }
        if (select instanceof SqlQuerySelect) {
            return (SqlQuerySelect) select;
        }
        if (select instanceof SqlUnionSelect) {
            return ((SqlUnionSelect) select).getFirstSelect();
        }
        return null;
    }

    /**
     * 从 FROM 子句构建 scopeName(lower) → simpleTableName 映射 + 源表计数（D1）。
     * 仅收集 {@link SqlSingleTableSource}（直查源表）；子查询源（{@code SqlSubqueryTableSource}）
     * 不收集——其别名引用的列无法归属（CTE/子查询列穿透 deferred）。
     */
    private AliasTableMap buildAliasTableMap(SqlFrom from) {
        Map<String, String> map = new LinkedHashMap<>();
        if (from == null || from.getTableSources() == null) {
            return new AliasTableMap(map, 0);
        }
        List<SqlSingleTableSource> sources = new ArrayList<>();
        for (SqlTableSource ts : from.getTableSources()) {
            ts.forEachTableSource(s -> {
                if (s instanceof SqlSingleTableSource) {
                    sources.add((SqlSingleTableSource) s);
                }
            });
        }
        for (SqlSingleTableSource src : sources) {
            SqlTableName tableName = src.getTableName();
            if (tableName == null || tableName.getName() == null || tableName.getName().isEmpty()) {
                continue;
            }
            String simple = tableName.getName();
            String scopeName = src.getScopeName();
            if (scopeName == null || scopeName.isEmpty()) {
                scopeName = simple;
            }
            map.putIfAbsent(scopeName.toLowerCase(), simple);
        }
        return new AliasTableMap(map, sources.size());
    }

    /** 输出列名：alias 优先 / 列名次之 / 表达式标记 &lt;expr_N&gt;（N 为表达式列自身序号）。 */
    private String resolveTargetColumn(SqlExprProjection proj, int exprIndex) {
        if (proj.getAlias() != null && proj.getAlias().getAlias() != null && !proj.getAlias().getAlias().isEmpty()) {
            return proj.getAlias().getAlias();
        }
        if (proj.getExpr() instanceof SqlColumnName) {
            String colName = ((SqlColumnName) proj.getExpr()).getName();
            if (colName != null && !colName.isEmpty()) {
                return colName;
            }
        }
        return "<expr_" + (exprIndex + 1) + ">";
    }

    /**
     * transformType 判定（D3）：聚合优先 {@code instanceof SqlAggregateFunction}（非函数名白名单）→ aggregated；
     * 直接列引用（expr 为 {@link SqlColumnName}）→ direct；其他（表达式/函数）→ derived。
     */
    private String resolveTransformType(EqlASTNode expr) {
        if (expr == null) {
            return _NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED;
        }
        if (expr instanceof SqlAggregateFunction) {
            return _NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED;
        }
        if (expr instanceof SqlColumnName) {
            return _NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT;
        }
        return _NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED;
    }

    /** 递归 walk expr 子树收集所有 {@link SqlColumnName} 节点（forEachChild 已递归）。 */
    private void collectColumnRefs(EqlASTNode node, List<SqlColumnName> out) {
        if (node == null) {
            return;
        }
        if (node instanceof SqlColumnName) {
            out.add((SqlColumnName) node);
            // SqlColumnName 的 owner 是限定符（表名），不再向下 walk（避免把 owner 当列引用）
            return;
        }
        node.forEachChild((Consumer<EqlASTNode>) child -> collectColumnRefs(child, out));
    }

    /**
     * 列引用归属解析（D1）：有 owner 限定符 → 查 alias→table 映射；无 owner 且单表 → 归属唯一源表；
     * 无 owner 且多表 → 不可归属（歧义，进 unresolved）。owner 未匹配映射（CTE/子查询别名）→ 不可归属。
     */
    private ColumnLineageCandidate attribute(SqlColumnName col, String targetColumn, String transformType,
                                              AliasTableMap aliasMap) {
        String sourceColumn = col.getName();
        String owner = col.getOwner() != null ? col.getOwner().getName() : null;
        if (owner != null && !owner.isEmpty()) {
            String sourceTable = aliasMap.map.get(owner.toLowerCase());
            if (sourceTable != null) {
                return ColumnLineageCandidate.resolved(targetColumn, sourceTable, sourceColumn, transformType);
            }
            // owner 限定符未匹配（可能是 CTE/子查询别名 / 动态 SQL）→ 不可归属（不伪造）
            return ColumnLineageCandidate.unresolvable(targetColumn, sourceColumn, "owner-not-matched:" + owner);
        }
        // 无限定符
        if (aliasMap.tableCount == 1 && !aliasMap.map.isEmpty()) {
            String onlyTable = aliasMap.map.values().iterator().next();
            return ColumnLineageCandidate.resolved(targetColumn, onlyTable, sourceColumn, transformType);
        }
        // 多表无限定符 → 歧义（不是仅同名列歧义，是多表即歧义）
        return ColumnLineageCandidate.unresolvable(targetColumn, sourceColumn,
                aliasMap.tableCount == 0 ? "no-from-source" : "ambiguous-column-multi-table");
    }

    /** scopeName(lower) → simpleTableName 映射 + 源表计数（用于无别名单表归属）。 */
    private static final class AliasTableMap {
        final Map<String, String> map;
        final int tableCount;

        AliasTableMap(Map<String, String> map, int tableCount) {
            this.map = map;
            this.tableCount = tableCount;
        }
    }
}
