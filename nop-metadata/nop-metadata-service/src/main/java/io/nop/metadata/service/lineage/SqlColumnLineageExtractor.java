/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.lineage;

import io.nop.api.core.exceptions.NopException;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlAggregateFunction;
import io.nop.orm.eql.ast.SqlAllProjection;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlCteStatement;
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
import io.nop.orm.eql.ast.SqlSubqueryTableSource;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlUnionSelect;
import io.nop.orm.eql.parse.EqlASTParser;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 列级 SQL 血缘解析器（架构基线 §2.6.1 列级 sql_parse，P2-5+ 裁定 D1/D3，含 CTE/派生表列穿透）：
 * 对 tableType=sql 的视图 sourceSql 做纯语法解析，将 SELECT 输出列映射回其引用的源表源列，
 * 返回 {@link ColumnLineageCandidate} 列表。
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
 * <p>CTE / 派生表列穿透（P2-5++）：
 * <ul>
 *   <li>CTE 定义：解析 {@link SqlSelectWithCte#getWithCtes()} 每个 CTE，递归解析其输出列到底层源列，
 *   建立 {@code cte 名(lower) → {输出列名(lower) → List&lt;(源表, 源列, transformType)&gt;}}。</li>
 *   <li>CTE 引用：CTE 引用解析为 {@link SqlSingleTableSource}（CTE 名=tableName，已进 aliasMap）。
 *   归属阶段对 owner 命中得到的「源表名」若匹配已注册 CTE 名，按引用列名查 CTE 输出列映射，
 *   对每个底层源列产出 resolved 候选（源表=底层源表 simpleName，transformType=透传）。</li>
 *   <li>派生表（{@link SqlSubqueryTableSource}）：在 {@code buildAliasTableMap} 同样处理——递归解析其输出列到底层源列，
 *   注册 {@code alias(lower) → {...}}，归属阶段命中 alias 时穿透到底层源列产出 resolved 候选。</li>
 *   <li>嵌套递归 + 环路守卫（已解析 CTE 名集合防自引用无限递归；{@code WITH RECURSIVE} 自引用整体 unsupported）。</li>
 *   <li>通配符输出 / 未匹配列 → unresolved（不伪造、不静默丢弃）。</li>
 * </ul>
 *
 * <p>不可解析形态显式标记 unresolved（不静默跳过、不伪造）：{@code SELECT *} 通配符、多表无限定符歧义。
 * SQL 为空/不可解析/多语句/非 SELECT → 显式抛 {@link NopException}。
 *
 * <p>本解析器无状态、无目录依赖（纯文本→候选列表）。
 */
public class SqlColumnLineageExtractor {


    /**
     * CTE / 派生表的「输出列 → 底层源列列表」映射条目：单条 = (源表 simpleName, 源列名, transformType)。
     * 一个 CTE/派生表输出列可能由多个底层源列聚合/复合而成（如 SUM(t1.a+t2.b) AS s → 2 条底层）。
     */
    static final class SourceRef {
        final String sourceTable;
        final String sourceColumn;
        final String transformType;

        SourceRef(String sourceTable, String sourceColumn, String transformType) {
            this.sourceTable = sourceTable;
            this.sourceColumn = sourceColumn;
            this.transformType = transformType;
        }
    }

    /** CTE 名(lower) / 派生表 alias(lower) → {输出列名(lower) → List&lt;SourceRef&gt;}。 */
    static final class NamedSourceMap {
        /** CTE 名 / 派生表 alias（仅用于错误信息；构造时为空，注册时回填）。 */
        String name;
        /** 输出列名(lower) → 底层源列列表。LinkedHashMap 保序。 */
        final Map<String, List<SourceRef>> outputs = new LinkedHashMap<>();
        /** 通配符输出（CTE/派生表 SELECT *）整体标 unresolved：true 时 outputs 不被信任。 */
        boolean wildcardOutput = false;

        NamedSourceMap(String name) {
            this.name = name;
        }
    }

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
            throw new NopMetadataException(NopMetadataErrors.ERR_COL_LINEAGE_SQL_EMPTY).param("sql", sql);
        }

        SqlProgram program;
        try {
            program = parser.parseFromText(null, sql);
        } catch (Exception e) {
            throw new NopMetadataException(NopMetadataErrors.ERR_COL_LINEAGE_SQL_PARSE_FAILED, e).param("sql", sql);
        }
        if (program == null || program.getStatements() == null || program.getStatements().isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_COL_LINEAGE_SQL_PARSE_FAILED).param("sql", sql);
        }

        List<SqlStatement> statements = program.getStatements();
        if (statements.size() != 1) {
            throw new NopMetadataException(NopMetadataErrors.ERR_COL_LINEAGE_MULTI_STATEMENT)
                    .param("count", statements.size()).param("sql", sql);
        }

        SqlStatement stmt = statements.get(0);
        if (stmt.getStatementKind() != SqlStatementKind.SELECT) {
            throw new NopMetadataException(NopMetadataErrors.ERR_COL_LINEAGE_NOT_SELECT)
                    .param("statementKind", String.valueOf(stmt.getStatementKind())).param("sql", sql);
        }

        // CTE 定义先行解析：建立 CTE 名 → 输出列映射（顶层 SqlSelectWithCte 才有 CTE）
        Map<String, NamedSourceMap> cteRegistry = new LinkedHashMap<>();
        if (stmt instanceof SqlSelectWithCte) {
            SqlSelectWithCte withCte = (SqlSelectWithCte) stmt;
            List<SqlCteStatement> ctes = withCte.getWithCtes();
            if (ctes != null) {
                Set<String> resolving = new HashSet<>();
                for (SqlCteStatement cte : ctes) {
                    if (cte == null || cte.getName() == null) {
                        continue;
                    }
                    String cteNameLower = cte.getName().toLowerCase();
                    if (cte.getRecursive()) {
                        // WITH RECURSIVE 自引用 CTE：纯语法无法判定终止，整体 unsupported（不展开、不报错，
                        // 穿透阶段命中时产 unresolved:recursive-cte）。注册空 NamedSourceMap + 标 wildcard 语义。
                        NamedSourceMap m = new NamedSourceMap(cte.getName());
                        m.wildcardOutput = true;
                        cteRegistry.put(cteNameLower, m);
                        continue;
                    }
                    if (resolving.contains(cteNameLower)) {
                        // 同名 CTE 重复定义（语法非法场景），跳过避免无限递归
                        continue;
                    }
                    resolving.add(cteNameLower);
                    NamedSourceMap m = resolveCteOrSubquery(cte.getStatement(), resolving, cteRegistry, sql);
                    cteRegistry.put(cteNameLower, m);
                    resolving.remove(cteNameLower);
                }
            }
        }

        // 解析出含 FROM 子句的 SqlQuerySelect（钻入 CTE / UNION firstSelect）
        SqlQuerySelect query = resolveQuerySelect(stmt);
        if (query == null) {
            // getStatementKind()==SELECT 但非已知子类——不可达，显式失败而非静默
            throw new NopMetadataException(NopMetadataErrors.ERR_COL_LINEAGE_SQL_PARSE_FAILED)
                    .param("sql", sql)
                    .param("reason", "unhandled SELECT statement class: " + stmt.getClass().getName());
        }

        // 构建 scopeName(lower) → simpleTableName 映射 + 源表计数 + 派生表 alias 映射
        AliasScope scope = buildAliasScope(query.getFrom(), new HashSet<>(), cteRegistry, sql);

        List<SqlProjection> projections = query.getProjections();
        // 平台对裸 `SELECT *` 会静默丢弃通配符返回空 projections；此处显式失败（不静默返回空候选列表，
        // 因合法显式列 SELECT 必有 ≥1 个 projection，空列表唯一现实成因是被丢弃的裸 *）。
        if (projections == null || projections.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_COL_LINEAGE_SQL_PARSE_FAILED)
                    .param("sql", sql)
                    .param("reason", "empty projections (bare wildcard '*' cannot be resolved)");
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
                throw new NopMetadataException(NopMetadataErrors.ERR_COL_LINEAGE_SQL_PARSE_FAILED)
                        .param("sql", sql)
                        .param("reason", "unhandled projection class: " + proj.getClass().getName());
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
                attribute(col, targetColumn, transformType, scope, cteRegistry, candidates);
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
        return asQuerySelect(select);
    }

    /** 取含 FROM 的 SqlQuerySelect（SqlUnionSelect 取 firstSelect 递归）。 */
    private SqlQuerySelect asQuerySelect(SqlSelect select) {
        if (select instanceof SqlQuerySelect) {
            return (SqlQuerySelect) select;
        }
        if (select instanceof SqlUnionSelect) {
            return ((SqlUnionSelect) select).getFirstSelect();
        }
        return null;
    }

    /**
     * 解析一个 CTE / 派生表的 SELECT 体，构造 {@link NamedSourceMap}：把每个输出列展开到底层源列。
     * 体本身可能是 {@link SqlUnionSelect} 或再含派生表（嵌套）——递归处理。
     *
     * <p>注：CTE / 派生表体的静态类型是 {@link SqlSelect}（{@code SqlSelectWithCte} extends {@code SqlDmlStatement}
     * 而非 {@code SqlSelect}，二者在 AST 上是兄弟类），故 CTE 内嵌套 WITH 在 AST 文法层不被产生——
     * 顶层 {@link SqlSelectWithCte#getWithCtes()} 已展开全部 CTE 定义，无需在 body 内再处理 WITH。
     *
     * @param selectBody CTE/派生表的 SELECT 体
     * @param resolving 正在解析的 CTE 名集合（环路守卫：自引用 CTE 命中此集合 → 跳过该引用）
     * @param cteRegistry 顶层 CTE 注册表（CTE 体内可引用更早定义的 CTE）
     * @param sql 原 SQL 文本（错误信息用）
     */
    private NamedSourceMap resolveCteOrSubquery(SqlSelect selectBody, Set<String> resolving,
                                                  Map<String, NamedSourceMap> cteRegistry, String sql) {
        NamedSourceMap result = new NamedSourceMap("");
        if (selectBody == null) {
            return result;
        }

        Map<String, NamedSourceMap> effectiveRegistry = cteRegistry;

        SqlQuerySelect querySelect = asQuerySelect(selectBody);
        if (querySelect == null) {
            // 体非可识别 SELECT（理论不可达，因 validate 保证）→ 返回空 map（穿透阶段按未注册处理）
            return result;
        }

        // 体里的派生表 alias 映射（体内可能 FROM (...) alias 嵌套）
        AliasScope bodyScope = buildAliasScope(querySelect.getFrom(), resolving, effectiveRegistry, sql);
        List<SqlProjection> projections = querySelect.getProjections();
        if (projections == null) {
            return result;
        }

        int bodyExprIndex = 0;
        for (SqlProjection proj : projections) {
            if (proj instanceof SqlAllProjection) {
                // 体输出通配符 *  → 整体标 wildcard，上层命中此 CTE/派生表 alias 的列穿透全部产 unresolved
                result.wildcardOutput = true;
                continue;
            }
            if (!(proj instanceof SqlExprProjection)) {
                continue;
            }
            SqlExprProjection exprProj = (SqlExprProjection) proj;
            String outCol = resolveTargetColumn(exprProj, bodyExprIndex);
            if (outCol.startsWith("<expr_")) {
                bodyExprIndex++;
            }
            String outTransform = resolveTransformType(exprProj.getExpr());
            List<SqlColumnName> bodyCols = new ArrayList<>();
            collectColumnRefs(exprProj.getExpr(), bodyCols);
            String outColLower = outCol.toLowerCase();
            if (bodyCols.isEmpty()) {
                // 常量列（如 SELECT 1 AS x）无源列 → 该输出列无源列可穿透（上层引用此列产 unresolved:null-source）
                result.outputs.put(outColLower, Collections.emptyList());
                continue;
            }
            List<SourceRef> refs = new ArrayList<>();
            for (SqlColumnName col : bodyCols) {
                expandColumnToSourceRefs(col, outTransform, bodyScope, effectiveRegistry, refs);
            }
            result.outputs.put(outColLower, refs);
        }
        return result;
    }

    /**
     * 把一个列引用（出现在 CTE/派生表体内）展开为底层源列列表。CTE/派生表 alias 命中 → 递归穿透；
     * 直查源表 → 直接产出 SourceRef；unresolvable 列 → 跳过（上层会因找不到 outputs 而标 unresolved）。
     */
    private void expandColumnToSourceRefs(SqlColumnName col, String outerTransform,
                                            AliasScope scope, Map<String, NamedSourceMap> cteRegistry,
                                            List<SourceRef> outRefs) {
        String sourceColumn = col.getName();
        String owner = col.getOwner() != null ? col.getOwner().getName() : null;

        // 限定符列：先看是否是派生表 alias（scope.derivedAliases），再 aliasMap（直查源表 or CTE 名）
        if (owner != null && !owner.isEmpty()) {
            String ownerLower = owner.toLowerCase();
            NamedSourceMap derived = scope.derivedAliases.get(ownerLower);
            if (derived != null) {
                flattenFromNamedMap(derived, sourceColumn, outerTransform, outRefs);
                return;
            }
            String simpleTable = scope.aliasMap.get(ownerLower);
            if (simpleTable != null) {
                // 若 simpleTable 命中已注册 CTE 名 → 穿透递归到底层源列（嵌套 CTE 引用）
                NamedSourceMap cte = cteRegistry.get(simpleTable.toLowerCase());
                if (cte != null) {
                    flattenFromNamedMap(cte, sourceColumn, outerTransform, outRefs);
                    return;
                }
                outRefs.add(new SourceRef(simpleTable, sourceColumn, outerTransform));
                return;
            }
            // owner 未匹配 → 该列无底层源列（上层标 unresolved）
            return;
        }
        // 无限定符
        if (scope.tableCount == 1 && !scope.aliasMap.isEmpty()) {
            String onlyTable = scope.aliasMap.values().iterator().next();
            outRefs.add(new SourceRef(onlyTable, sourceColumn, outerTransform));
        }
        // 多表无 owner → 不可归属（无底层源列可穿透，上层标 unresolved）
    }

    /**
     * 从 CTE/派生表的 NamedSourceMap 按列名取得底层源列列表，加入 outRefs。
     * transformType 透传规则：CTE 内聚合列 → aggregated；纯透传 → 继承底层 transformType。
     * 实现：NamedSourceMap 里已存了体内解析得到的 transformType（含 aggregated 标记），直接透传。
     * 但若 outer（外层引用）是 aggregated，覆盖为 aggregated（聚合外推优先级高）。
     * 通配符输出 / 未匹配列 → 调用方按 unresolved 处理。
     */
    private void flattenFromNamedMap(NamedSourceMap namedMap, String colName, String outerTransform,
                                       List<SourceRef> outRefs) {
        if (namedMap.wildcardOutput) {
            // 通配符输出 → 无法穿透具体列（上层标 unresolved）
            return;
        }
        List<SourceRef> refs = namedMap.outputs.get(colName.toLowerCase());
        if (refs == null || refs.isEmpty()) {
            return;
        }
        for (SourceRef r : refs) {
            // 透传 transformType：聚合优先（外层或底层任一为 aggregated → aggregated），
            // 否则若外层是 derived/aggregated 用外层（外层语义优先），否则继承底层。
            String tt = mergeTransformType(outerTransform, r.transformType);
            outRefs.add(new SourceRef(r.sourceTable, r.sourceColumn, tt));
        }
    }

    /** transformType 合并：aggregated 优先（任一为聚合 → aggregated）；其次 derived；否则保留 outer。 */
    private static String mergeTransformType(String outer, String inner) {
        if (_NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED.equals(outer)
                || _NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED.equals(inner)) {
            return _NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED;
        }
        if (_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED.equals(outer)
                || _NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED.equals(inner)) {
            return _NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED;
        }
        return outer != null ? outer : _NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT;
    }

    /**
     * 从 FROM 子句构建 alias 作用域（D1 + CTE/派生表穿透 P2-5++）。
     * <ul>
     *   <li>{@link SqlSingleTableSource}：注册 {@code scopeName(lower) → simpleTableName}（直查源表或 CTE 名）。</li>
     *   <li>{@link SqlSubqueryTableSource}（派生表）：递归解析输出列 → 底层源列，注册 alias → {@link NamedSourceMap}。</li>
     *   <li>{@code tableCount}：仅计 {@link SqlSingleTableSource} 数（直查源表；派生表 alias 不计入单表归属判定）。</li>
     * </ul>
     */
    private AliasScope buildAliasScope(SqlFrom from, Set<String> resolving,
                                         Map<String, NamedSourceMap> cteRegistry, String sql) {
        Map<String, String> aliasMap = new LinkedHashMap<>();
        Map<String, NamedSourceMap> derivedAliases = new LinkedHashMap<>();
        int singleCount = 0;
        if (from == null || from.getTableSources() == null) {
            return new AliasScope(aliasMap, derivedAliases, 0);
        }
        for (SqlTableSource ts : from.getTableSources()) {
            // 收集所有源（含 Join 的 left/right）——派生表别名也要正确归属
            List<SqlTableSource> flatSources = new ArrayList<>();
            ts.forEachTableSource(flatSources::add);
            for (SqlTableSource s : flatSources) {
                if (s instanceof SqlSingleTableSource) {
                    SqlSingleTableSource src = (SqlSingleTableSource) s;
                    SqlTableName tableName = src.getTableName();
                    if (tableName == null || tableName.getName() == null || tableName.getName().isEmpty()) {
                        continue;
                    }
                    String simple = tableName.getName();
                    String scopeName = src.getScopeName();
                    if (scopeName == null || scopeName.isEmpty()) {
                        scopeName = simple;
                    }
                    aliasMap.putIfAbsent(scopeName.toLowerCase(), simple);
                    singleCount++;
                } else if (s instanceof SqlSubqueryTableSource) {
                    SqlSubqueryTableSource sub = (SqlSubqueryTableSource) s;
                    String alias = null;
                    if (sub.getAlias() != null) {
                        alias = sub.getAlias().getAlias();
                    }
                    if (alias == null || alias.isEmpty()) {
                        // 派生表无 alias → 无法在 projection 中被限定符引用，跳过（不是错误，可能只是 FROM 子句存在）
                        continue;
                    }
                    NamedSourceMap m = resolveCteOrSubquery(sub.getQuery(), resolving, cteRegistry, sql);
                    m.name = alias;
                    derivedAliases.putIfAbsent(alias.toLowerCase(), m);
                }
            }
        }
        return new AliasScope(aliasMap, derivedAliases, singleCount);
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
     * 列引用归属解析（D1 + CTE/派生表穿透 P2-5++）：把一个列引用产出的候选追加到 {@code out}。
     * <ul>
     *   <li>有 owner 限定符：先查派生表 alias → CTE 名 → 直查源表 aliasMap。命中即穿透。</li>
     *   <li>无 owner 且单表：归属唯一源表。</li>
     *   <li>无 owner 且多表：歧义，unresolved。</li>
     *   <li>CTE/派生表穿透后产出多个底层源列时，每个产出一条 resolved 候选；通配符/未匹配产 unresolved。</li>
     * </ul>
     */
    private void attribute(SqlColumnName col, String targetColumn, String transformType,
                             AliasScope scope, Map<String, NamedSourceMap> cteRegistry,
                             List<ColumnLineageCandidate> out) {
        String sourceColumn = col.getName();
        String owner = col.getOwner() != null ? col.getOwner().getName() : null;
        if (owner != null && !owner.isEmpty()) {
            String ownerLower = owner.toLowerCase();
            // 1) 派生表 alias（SqlSubqueryTableSource 的 alias，不在 aliasMap）
            NamedSourceMap derived = scope.derivedAliases.get(ownerLower);
            if (derived != null) {
                emitFromNamedMap(derived, sourceColumn, targetColumn, transformType, "derived-table-wildcard:"
                        + owner + "." + sourceColumn, out);
                return;
            }
            // 2) 直查源表 / CTE 引用（CTE 引用解析为 SqlSingleTableSource，aliasMap[owner] = CTE 名）
            String sourceTable = scope.aliasMap.get(ownerLower);
            if (sourceTable != null) {
                // 2a) 若 sourceTable 命中已注册 CTE 名 → 按引用列名穿透到底层源列（CTE 列穿透关键修正）
                NamedSourceMap cte = cteRegistry.get(sourceTable.toLowerCase());
                if (cte != null) {
                    emitFromNamedMap(cte, sourceColumn, targetColumn, transformType, "cte-wildcard:"
                            + sourceTable + "." + sourceColumn, out);
                    return;
                }
                // 2b) 直查物理源表
                out.add(ColumnLineageCandidate.resolved(targetColumn, sourceTable, sourceColumn, transformType));
                return;
            }
            // owner 限定符未匹配（动态 SQL / 未注册别名）→ 不可归属（不伪造）
            out.add(ColumnLineageCandidate.unresolvable(targetColumn, sourceColumn, "owner-not-matched:" + owner));
            return;
        }
        // 无限定符
        if (scope.tableCount == 1 && !scope.aliasMap.isEmpty()) {
            String onlyTable = scope.aliasMap.values().iterator().next();
            out.add(ColumnLineageCandidate.resolved(targetColumn, onlyTable, sourceColumn, transformType));
            return;
        }
        // 多表无限定符 → 歧义
        out.add(ColumnLineageCandidate.unresolvable(targetColumn, sourceColumn,
                scope.tableCount == 0 ? "no-from-source" : "ambiguous-column-multi-table"));
    }

    /**
     * 把 CTE/派生表 {@link NamedSourceMap} 按引用列名展开为底层源列候选（emit 到 out）。
     * 通配符输出 / recursive CTE / 未匹配列 / 未解析列 → unresolved（不伪造）。
     */
    private void emitFromNamedMap(NamedSourceMap namedMap, String sourceColumn, String targetColumn,
                                    String outerTransform, String wildcardReason,
                                    List<ColumnLineageCandidate> out) {
        if (namedMap.wildcardOutput) {
            // recursive CTE 或通配符输出 → 显式 unresolved
            out.add(ColumnLineageCandidate.unresolvable(targetColumn, sourceColumn, wildcardReason));
            return;
        }
        List<SourceRef> refs = namedMap.outputs.get(sourceColumn.toLowerCase());
        if (refs == null) {
            // 引用列在 CTE/派生表输出中不存在（如通配符 SELECT * 体内或别名列名不匹配）
            out.add(ColumnLineageCandidate.unresolvable(targetColumn, sourceColumn,
                    "cte-or-derived-column-not-found:" + namedMap.name + "." + sourceColumn));
            return;
        }
        if (refs.isEmpty()) {
            // 输出列存在但无底层源列（常量列）→ 无血缘边候选
            out.add(ColumnLineageCandidate.unresolvable(targetColumn, sourceColumn,
                    "passthrough-no-source-column:" + namedMap.name + "." + sourceColumn));
            return;
        }
        for (SourceRef r : refs) {
            String tt = mergeTransformType(outerTransform, r.transformType);
            out.add(ColumnLineageCandidate.resolved(targetColumn, r.sourceTable, r.sourceColumn, tt));
        }
    }

    /** 归属作用域：alias→直查源表 simpleName、派生表 alias→NamedSourceMap、直查源表计数（单表归属判定）。 */
    private static final class AliasScope {
        final Map<String, String> aliasMap;
        final Map<String, NamedSourceMap> derivedAliases;
        final int tableCount;

        AliasScope(Map<String, String> aliasMap, Map<String, NamedSourceMap> derivedAliases, int tableCount) {
            this.aliasMap = aliasMap;
            this.derivedAliases = derivedAliases;
            this.tableCount = tableCount;
        }
    }
}
