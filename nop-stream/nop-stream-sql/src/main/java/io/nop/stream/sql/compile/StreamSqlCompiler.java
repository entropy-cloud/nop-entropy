/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlAggregateFunction;
import io.nop.orm.eql.ast.SqlAllProjection;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlFrom;
import io.nop.orm.eql.ast.SqlGroupBy;
import io.nop.orm.eql.ast.SqlGroupByItem;
import io.nop.orm.eql.ast.SqlJoinTableSource;
import io.nop.orm.eql.ast.SqlNotExpr;
import io.nop.orm.eql.ast.SqlIsNullExpr;
import io.nop.orm.eql.ast.SqlBetweenExpr;
import io.nop.orm.eql.ast.SqlInValuesExpr;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlSelect;
import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlSubqueryTableSource;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlTumbleTableSource;
import io.nop.orm.eql.ast.SqlUnaryExpr;
import io.nop.orm.eql.ast.SqlOrExpr;
import io.nop.orm.eql.ast.SqlUnionSelect;
import io.nop.orm.eql.ast.SqlWhere;
import io.nop.orm.eql.enums.SqlJoinType;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.enums.SqlUnionType;
import io.nop.orm.eql.parse.EqlASTParser;
import io.nop.orm.eql.parse.EqlParseHelper;
import io.nop.stream.sql.eval.SqlRowAggregateOps;
import io.nop.stream.sql.eval.SqlRowAggregateSpec;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WI17: compiles the stream SQL v1 subset (sql-subset-and-semantics.md §4b) from an
 * EQL AST into a complete stream-model XML consumable by the existing xdef validation
 * and {@code StreamModelDslBuilder}. Entry point is the shared {@link EqlASTParser}
 * (D7); FROM table name = source bean name (r2 B2 — the application registers a
 * {@code SourceFunction} bean per table name); the compiled row carrier is a
 * {@code Map} keyed by the SELECT output names (r2 M3 v1 contract).
 *
 * <h3>Aggregation dispatch (r2 B3)</h3>
 * <ul>
 *   <li>TUMBLE present → keyBy(groupKeys) + window(duration) + aggregate over the
 *       synthesized composite aggregator entry ({@code fnId=sql-row-agg}, r2 B1).</li>
 *   <li>No TUMBLE, GROUP BY → continuous pipeline map(record→accumulator-row) + keyBy
 *       + reduce(merge) + map(row) — last-value-wins final-value semantics (D1); the
 *       emitted xpl bodies dispatch to {@link SqlRowAggregateOps}, never
 *       re-implementing accumulation.</li>
 *   <li>Global aggregation (no GROUP BY) → fail-fast.</li>
 * </ul>
 *
 * <h3>Fail-fast matrix (r2 M4)</h3>
 * Every unsupported construct fails fast — default-reject with
 * {@code nop.err.stream.invalid-arg} (HAVING, CTE/WITH, DISTINCT, INTERSECT/EXCEPT,
 * parenthesized/LATERAL selects, SELECT * , global aggregation, non-equi join,
 * GROUP BY without aggregate, TUMBLE+join, aggregate+join, multi-statement,
 * non-managed schema types), ORDER BY / LIMIT with the pinned
 * {@code nop.err.eql.dialect-not-support-feature}. UNION is bag union (no dedup);
 * UNION DISTINCT fails fast. No AST walk branch silently drops a construct.
 */
public final class StreamSqlCompiler {

    /**
     * Managed type name closed set (D7 §1.3) — anything else fails fast.
     */
    private static final Set<String> MANAGED_TYPES = Set.of(
            "string", "int", "bigint", "smallint", "tinyint", "float", "double", "boolean", "bytes");

    private static final String SCHEMA_ID = "sqlschema0";
    private static final String STRATEGY_ID = "ws";
    private static final String AGGREGATOR_ID = "aggr";
    private static final String JOIN_SPEC_ID = "jspec";

    private StreamSqlCompiler() {
    }

    /**
     * Compiles one SQL query into a complete stream-model XML string (transforms,
     * edges, registries, and the sink derived from {@code sinkBean} — the product is
     * self-consistent and executable, r2 B4).
     *
     * @param sql      the SQL text (one statement)
     * @param schema   the SQL-side self-carried schema: column name → managed type name
     *                 (D7 §1.3); may be null/empty (no schema declared)
     * @param sinkBean the bean name of the sink the product appends (never blank)
     * @return the model XML text
     */
    public static String compile(SourceLocation loc, String sql, Map<String, String> schema,
                                 String sinkBean) {
        if (sql == null || sql.trim().isEmpty()) {
            throw CompileErrors.invalidArg("SQL text is blank");
        }
        if (sinkBean == null || sinkBean.trim().isEmpty()) {
            throw CompileErrors.invalidArg("sinkBean is required: the compiled model appends "
                    + "a <sink> declaration and edge so the product is self-consistent");
        }
        SqlProgram program = new EqlASTParser().parseFromText(loc, sql);
        if (program == null || program.getStatements() == null
                || program.getStatements().isEmpty()) {
            throw CompileErrors.invalidArg("SQL text parses to no statement: '" + sql + "'");
        }
        if (program.getStatements().size() > 1) {
            throw CompileErrors.invalidArg("multi-statement SQL is outside the stream SQL v1 subset");
        }
        validateSchema(schema);

        XNode stream = XNode.make(loc, "stream");
        stream.setAttr("x:schema", "/nop/schema/stream/stream.xdef");
        stream.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");
        stream.setAttr("name", "sql-compiled");
        stream.setAttr("version", 1);

        CompileContext ctx = new CompileContext(schema);
        List<XNode> transforms = new ArrayList<>();
        List<XNode> edges = new ArrayList<>();
        String lastId = compileStatement(program.getStatements().get(0),
                ctx, transforms, edges, "s");

        appendSink(lastId, sinkBean, transforms, edges);

        if (!ctx.schemaFields.isEmpty()) {
            stream.appendChild(buildSchemasNode(loc, ctx));
        }
        if (!ctx.aggregatorEntries.isEmpty()) {
            XNode aggs = XNode.make(loc, "aggregators");
            ctx.aggregatorEntries.forEach(aggs::appendChild);
            stream.appendChild(aggs);
        }
        if (!ctx.joinSpecs.isEmpty()) {
            XNode joins = XNode.make(loc, "joins");
            ctx.joinSpecs.forEach(joins::appendChild);
            stream.appendChild(joins);
        }
        if (!ctx.strategies.isEmpty()) {
            XNode strategies = XNode.make(loc, "windowingStrategies");
            ctx.strategies.forEach(strategies::appendChild);
            stream.appendChild(strategies);
        }
        XNode transformsNode = XNode.make(loc, "transforms");
        transforms.forEach(transformsNode::appendChild);
        stream.appendChild(transformsNode);
        XNode edgesNode = XNode.make(loc, "edges");
        edges.forEach(edgesNode::appendChild);
        stream.appendChild(edgesNode);

        return stream.xml();
    }

    // ----------------------------------------------------------------
    // statement dispatch
    // ----------------------------------------------------------------

    private static String compileStatement(io.nop.orm.eql.ast.SqlStatement stmt,
                                           CompileContext ctx,
                                           List<XNode> transforms, List<XNode> edges,
                                           String idPrefix) {
        if (stmt instanceof SqlSelectWithCte) {
            throw CompileErrors.invalidArg("WITH/CTE is outside the stream SQL v1 subset");
        }
        if (stmt instanceof SqlUnionSelect) {
            return compileUnion((SqlUnionSelect) stmt, ctx, transforms, edges, idPrefix);
        }
        if (stmt instanceof SqlQuerySelect) {
            return compileQuery((SqlQuerySelect) stmt, ctx, transforms, edges, idPrefix);
        }
        throw CompileErrors.invalidArg("statement type " + stmt.getClass().getSimpleName()
                + " is outside the stream SQL v1 subset");
    }

    private static String compileUnion(SqlUnionSelect union, CompileContext ctx,
                                       List<XNode> transforms, List<XNode> edges,
                                       String idPrefix) {
        // UNION semantics (r2 B3/Goals): UNION = bag union, no dedup. UNION DISTINCT
        // fails fast; INTERSECT/EXCEPT are outside the v1 subset entirely.
        if (union.getUnionType() != SqlUnionType.UNION_ALL) {
            throw CompileErrors.invalidArg("union type " + union.getUnionType()
                    + " is outside the stream SQL v1 subset (only UNION ALL — bag union, no dedup)");
        }
        String leftLast = compileStatement(union.getLeft(), ctx, transforms, edges, idPrefix + "0");
        String rightLast = compileStatement(union.getRight(), ctx, transforms, edges, idPrefix + "1");
        String unionId = idPrefix + "u";
        transforms.add(mk(loc(union), "union", "id", unionId));
        edges.add(edge(loc(union), idPrefix + "e0", leftLast, unionId));
        edges.add(edge(loc(union), idPrefix + "e1", rightLast, unionId));
        return unionId;
    }

    // ----------------------------------------------------------------
    // single query
    // ----------------------------------------------------------------

    private static String compileQuery(SqlQuerySelect select, CompileContext ctx,
                                       List<XNode> transforms, List<XNode> edges,
                                       String idPrefix) {
        if (select.getDistinct()) {
            throw CompileErrors.invalidArg("SELECT DISTINCT is outside the stream SQL v1 subset");
        }
        if (select.getHaving() != null) {
            throw CompileErrors.invalidArg("HAVING is outside the stream SQL v1 subset");
        }
        if (select.getWindowClause() != null) {
            throw CompileErrors.invalidArg("WINDOW clause (analysis window) is outside the "
                    + "stream SQL v1 subset; OVER expressions fail fast the same way");
        }
        if (select.getForUpdate()) {
            throw CompileErrors.invalidArg("FOR UPDATE is outside the stream SQL v1 subset");
        }
        if (select.getOrderBy() != null) {
            throw CompileErrors.dialectNotSupport("ORDER BY");
        }
        if (select.getLimit() != null) {
            throw CompileErrors.dialectNotSupport("LIMIT");
        }
        SqlFrom from = select.getFrom();
        if (from == null || from.getTableSources() == null
                || from.getTableSources().size() != 1) {
            throw CompileErrors.invalidArg("exactly one FROM table source is required");
        }
        SqlTableSource table = from.getTableSources().get(0);
        if (table instanceof SqlJoinTableSource) {
            return compileJoinQuery(select, (SqlJoinTableSource) table, ctx, transforms, edges,
                    idPrefix);
        }
        if (table instanceof SqlSubqueryTableSource) {
            throw CompileErrors.invalidArg("parenthesized/LATERAL subquery table source is "
                    + "outside the stream SQL v1 subset");
        }
        if (!(table instanceof SqlSingleTableSource)
                && !(table instanceof SqlTumbleTableSource)) {
            throw CompileErrors.invalidArg("table source type " + table.getClass().getSimpleName()
                    + " is outside the stream SQL v1 subset");
        }

        // WHERE is scanned first: aggregates are never allowed in WHERE (strict SQL) —
        // the filter printer has no aggregate resolver, so aggregates fail fast there.
        String filterId = null;
        if (select.getWhere() != null) {
            String filter = printWhere(select.getWhere().getExpr());
            filterId = idPrefix + "flt";
            XNode filterNode = mk(loc(select.getWhere()), "filter", "id", filterId);
            filterNode.appendChild(textNode(loc(select.getWhere()), "source",
                    "return " + filter + ";"));
            transforms.add(filterNode);
        }

        AggregateScan scan = scanProjections(select);
        boolean hasTumble = table instanceof SqlTumbleTableSource;

        if (scan.aggregates.isEmpty()) {
            if (select.getGroupBy() != null) {
                // GROUP BY without any aggregate would need dedup — not expressible in v1
                throw CompileErrors.invalidArg("GROUP BY requires at least one aggregate "
                        + "in the stream SQL v1 subset");
            }
            return compilePlainPipeline(select, table, filterId, scan, ctx, transforms, edges,
                    idPrefix);
        }

        // aggregation path (single table source only)
        if (select.getGroupBy() == null) {
            throw CompileErrors.invalidArg("global aggregation (aggregate without GROUP BY) "
                    + "is outside the stream SQL v1 subset");
        }
        GroupedAggregate grouped = buildGroupedAggregate(select, scan, table, hasTumble);
        return hasTumble
                ? compileWindowedAggregate(select, (SqlTumbleTableSource) table, filterId,
                        grouped, ctx, transforms, edges, idPrefix)
                : compileContinuousAggregate(select, (SqlSingleTableSource) table, filterId,
                        grouped, ctx, transforms, edges, idPrefix);
    }

    // ----------------------------------------------------------------
    // plain (non-aggregate) pipeline
    // ----------------------------------------------------------------

    private static String compilePlainPipeline(SqlQuerySelect select, SqlTableSource table,
                                               String filterId, AggregateScan scan,
                                               CompileContext ctx, List<XNode> transforms,
                                               List<XNode> edges, String idPrefix) {
        SqlTumbleTableSource tumble = table instanceof SqlTumbleTableSource
                ? (SqlTumbleTableSource) table : null;
        String tableName = tumble != null ? tumble.getTableName()
                : ((SqlSingleTableSource) table).getTableName().getName();
        String srcId = idPrefix + "src";
        transforms.add(sourceNode(loc(table), srcId, tableName));

        String prev = srcId;
        if (tumble != null) {
            String tswId = idPrefix + "tsw";
            transforms.add(timestampsNode(tumble, tswId));
            edges.add(edge(loc(tumble), idPrefix + "e0", prev, tswId));
            prev = tswId;
        }
        if (filterId != null) {
            edges.add(edge(loc(select), idPrefix + "e1", prev, filterId));
            prev = filterId;
        }
        String projId = idPrefix + "proj";
        transforms.add(projectionNode(select, scan, null, idPrefix + "e2", projId,
                singleInputResolver()));
        edges.add(edge(loc(select), idPrefix + "e3", prev, projId));
        return projId;
    }

    // ----------------------------------------------------------------
    // windowed aggregate (TUMBLE)
    // ----------------------------------------------------------------

    private static String compileWindowedAggregate(SqlQuerySelect select,
                                                   SqlTumbleTableSource tumble,
                                                   String filterId, GroupedAggregate grouped,
                                                   CompileContext ctx, List<XNode> transforms,
                                                   List<XNode> edges, String idPrefix) {
        String srcId = idPrefix + "src";
        transforms.add(sourceNode(loc(tumble), srcId, tumble.getTableName()));
        String tswId = idPrefix + "tsw";
        transforms.add(timestampsNode(tumble, tswId));
        edges.add(edge(loc(tumble), idPrefix + "e0", srcId, tswId));

        String prev = tswId;
        if (filterId != null) {
            edges.add(edge(loc(select), idPrefix + "e1", prev, filterId));
            prev = filterId;
        }
        String keyId = idPrefix + "key";
        transforms.add(mk(loc(select), "keyBy", "id", keyId, "keyExpr",
                keyExprText(grouped.keyXplExprs)));
        edges.add(edge(loc(select), idPrefix + "e2", prev, keyId));

        String strategyId = ctx.nextStrategy(STRATEGY_ID, grouped.durationMillis);
        String winId = idPrefix + "win";
        transforms.add(mk(loc(select), "window", "id", winId, "strategyRef", strategyId));
        edges.add(edge(loc(select), idPrefix + "e3", keyId, winId));

        String aggregatorId = ctx.nextAggregator(AGGREGATOR_ID, grouped.spec,
                !ctx.schemaFields.isEmpty());
        String aggId = idPrefix + "agg";
        transforms.add(mk(loc(select), "aggregate", "id", aggId, "aggregatorRef", aggregatorId));
        edges.add(edge(loc(select), idPrefix + "e4", winId, aggId));

        String projId = idPrefix + "proj";
        transforms.add(projectionNode(select, grouped.scan, grouped, idPrefix + "e5", projId,
                aggregatedRowResolver(grouped)));
        edges.add(edge(loc(select), idPrefix + "e6", aggId, projId));
        return projId;
    }

    // ----------------------------------------------------------------
    // continuous aggregate (no TUMBLE, GROUP BY) — r2 B3
    // ----------------------------------------------------------------

    private static String compileContinuousAggregate(SqlQuerySelect select,
                                                     SqlSingleTableSource base,
                                                     String filterId, GroupedAggregate grouped,
                                                     CompileContext ctx, List<XNode> transforms,
                                                     List<XNode> edges, String idPrefix) {
        String srcId = idPrefix + "src";
        transforms.add(sourceNode(loc(base), srcId, base.getTableName().getName()));
        String prev = srcId;
        if (filterId != null) {
            edges.add(edge(loc(select), idPrefix + "e1", prev, filterId));
            prev = filterId;
        }
        // map(record → accumulator row): the emitted body dispatches to the composite
        // function — the xpl never re-implements accumulation semantics.
        String beginId = idPrefix + "beg";
        XNode begin = mk(loc(select), "map", "id", beginId);
        begin.appendChild(textNode(loc(select), "source",
                "import " + SqlRowAggregateOps.class.getName() + "; return "
                        + SqlRowAggregateOps.class.getSimpleName() + ".begin("
                        + xlangString(grouped.spec.toJson()) + ", event);"));
        transforms.add(begin);
        edges.add(edge(loc(select), idPrefix + "e2", prev, beginId));

        // keyBy on the accumulator row head (group keys are stored first)
        String keyId = idPrefix + "key";
        transforms.add(mk(loc(select), "keyBy", "id", keyId, "keyExpr",
                rowHeadKeyExpr(grouped.keyCount())));
        edges.add(edge(loc(select), idPrefix + "e3", beginId, keyId));

        String redId = idPrefix + "red";
        XNode reduce = mk(loc(select), "reduce", "id", redId);
        reduce.appendChild(textNode(loc(select), "source",
                "import " + SqlRowAggregateOps.class.getName() + "; return "
                        + SqlRowAggregateOps.class.getSimpleName() + ".merge("
                        + xlangString(grouped.spec.toJson()) + ", a, b);"));
        transforms.add(reduce);
        edges.add(edge(loc(select), idPrefix + "e4", keyId, redId));

        String rowId = idPrefix + "row";
        XNode row = mk(loc(select), "map", "id", rowId);
        row.appendChild(textNode(loc(select), "source",
                "import " + SqlRowAggregateOps.class.getName() + "; return "
                        + SqlRowAggregateOps.class.getSimpleName() + ".toRow("
                        + xlangString(grouped.spec.toJson()) + ", event);"));
        transforms.add(row);
        edges.add(edge(loc(select), idPrefix + "e5", redId, rowId));

        String projId = idPrefix + "proj";
        transforms.add(projectionNode(select, grouped.scan, grouped, idPrefix + "e6", projId,
                aggregatedRowResolver(grouped)));
        edges.add(edge(loc(select), idPrefix + "e7", rowId, projId));
        return projId;
    }

    // ----------------------------------------------------------------
    // join (r2 M3)
    // ----------------------------------------------------------------

    private static String compileJoinQuery(SqlQuerySelect select, SqlJoinTableSource join,
                                           CompileContext ctx, List<XNode> transforms,
                                           List<XNode> edges, String idPrefix) {
        if (hasAggregate(select.getProjections())) {
            throw CompileErrors.invalidArg("aggregate over a join is outside the stream SQL v1 subset");
        }
        if (select.getGroupBy() != null) {
            throw CompileErrors.invalidArg("GROUP BY over a join is outside the stream SQL v1 subset");
        }
        SideRef left = requireJoinSide(join.getLeft(), "left");
        SideRef right = requireJoinSide(join.getRight(), "right");
        if (left.tumble || right.tumble) {
            throw CompileErrors.invalidArg("TUMBLE over a join is outside the stream SQL v1 subset");
        }

        JoinKeys keys = decomposeOnCondition(join, left, right);
        String joinType = joinTypeName(join.getJoinType());

        String leftId = idPrefix + "lsrc";
        String rightId = idPrefix + "rsrc";
        String joinId = idPrefix + "join";
        transforms.add(sourceNode(loc(left.source), leftId, left.tableName));
        transforms.add(sourceNode(loc(right.source), rightId, right.tableName));

        String specId = ctx.nextJoinSpec(JOIN_SPEC_ID, joinType, keys.leftKeyExprs,
                keys.rightKeyExprs);
        transforms.add(mk(join.getLocation(), "join", "id", joinId, "joinRef", specId));
        edges.add(edge(loc(join), idPrefix + "e0", leftId, joinId));
        edges.add(edge(loc(join), idPrefix + "e1", rightId, joinId));

        String prev = joinId;
        if (select.getWhere() != null) {
            String filter = joinWhere(select);
            String filterId = idPrefix + "flt";
            XNode filterNode = mk(loc(select.getWhere()), "filter", "id", filterId);
            filterNode.appendChild(textNode(loc(select.getWhere()), "source",
                    "return " + filter + ";"));
            transforms.add(filterNode);
            edges.add(edge(loc(select), idPrefix + "e2", prev, filterId));
            prev = filterId;
        }
        String projId = idPrefix + "proj";
        transforms.add(projectionNode(select, scanNoAggregate(select), null, idPrefix + "e3",
                projId, joinProjectionResolver(left, right)));
        edges.add(edge(loc(select), idPrefix + "e4", prev, projId));
        return projId;
    }

    private static String joinWhere(SqlQuerySelect select) {
        return new SqlScalarXplPrinter(joinProjectionResolver(
                new SideRef("__none_l__", null, false, null),
                new SideRef("__none_r__", null, false, null)), null)
                .print(select.getWhere().getExpr());
    }

    private static SideRef requireJoinSide(SqlTableSource source, String side) {
        if (source instanceof SqlSingleTableSource) {
            SqlSingleTableSource s = (SqlSingleTableSource) source;
            return new SideRef(scopeName(s.getAlias(), s.getTableName().getName()),
                    s.getTableName().getName(), false, s);
        }
        if (source instanceof SqlTumbleTableSource) {
            SqlTumbleTableSource t = (SqlTumbleTableSource) source;
            return new SideRef(scopeName(t.getAlias(), t.getTableName()), t.getTableName(), true, t);
        }
        throw CompileErrors.invalidArg("join " + side + " side must be a plain table source, got "
                + source.getClass().getSimpleName());
    }

    private static String scopeName(io.nop.orm.eql.ast.SqlAlias alias, String fallback) {
        return alias != null && alias.getAlias() != null ? alias.getAlias() : fallback;
    }

    /**
     * r2 M3: decomposes the ON condition into conjunct equi-pairs. Every conjunct must
     * be an equality of two qualified column references, one per side — non-equi or
     * same-side conjuncts fail fast (4a#2 non-equi join exclusion).
     */
    private static JoinKeys decomposeOnCondition(SqlJoinTableSource join, SideRef left,
                                                 SideRef right) {
        List<String> leftKeys = new ArrayList<>();
        List<String> rightKeys = new ArrayList<>();
        for (SqlExpr conjunct : conjuncts(join.getCondition())) {
            if (!(conjunct instanceof SqlBinaryExpr)) {
                throw CompileErrors.invalidArg("join ON conjunct must be an equality of two "
                        + "qualified columns (non-equi join is outside the v1 subset)");
            }
            SqlBinaryExpr bin = (SqlBinaryExpr) conjunct;
            if (bin.getOperator() != SqlOperator.EQ) {
                throw CompileErrors.invalidArg("join ON supports equi-join only, got operator "
                        + bin.getOperator());
            }
            // each operand resolves to (side, keyExpr); assignment follows the scopes,
            // so ON l.k = r.k and ON r.k = l.k both produce the correct per-side keys —
            // identical key TEXTS across sides are legal (self-join on the same column)
            SideKey a = sideColumnRef(bin.getLeft(), left, right);
            SideKey b = sideColumnRef(bin.getRight(), left, right);
            if (a == null || b == null || a.isLeft == b.isLeft) {
                throw CompileErrors.invalidArg("join ON supports qualified column equality only: "
                        + "one operand must belong to the left scope '" + left.scope
                        + "' and the other to the right scope '" + right.scope + "'");
            }
            SideKey lk = a.isLeft ? a : b;
            SideKey rk = a.isLeft ? b : a;
            leftKeys.add(lk.expr);
            rightKeys.add(rk.expr);
        }
        if (leftKeys.isEmpty()) {
            throw CompileErrors.invalidArg("join ON condition is missing (equi-join keys required)");
        }
        return new JoinKeys(String.join(",", leftKeys), String.join(",", rightKeys));
    }

    private static class SideKey {
        final boolean isLeft;
        final String expr;

        SideKey(boolean isLeft, String expr) {
            this.isLeft = isLeft;
            this.expr = expr;
        }
    }

    /**
     * Resolves an ON operand to its side and xpl key-expr text, or null when the
     * operand is not a column of either scope.
     */
    private static SideKey sideColumnRef(SqlExpr expr, SideRef left, SideRef right) {
        if (!(expr instanceof SqlColumnName))
            return null;
        SqlColumnName col = (SqlColumnName) expr;
        String owner = qualifier(col);
        if (owner == null)
            return null;
        String exprText = "event['" + lastSegment(col) + "']";
        if (owner.equalsIgnoreCase(left.scope))
            return new SideKey(true, exprText);
        if (owner.equalsIgnoreCase(right.scope))
            return new SideKey(false, exprText);
        return null;
    }

    private static List<SqlExpr> conjuncts(SqlExpr expr) {
        List<SqlExpr> list = new ArrayList<>();
        collectConjuncts(expr, list);
        return list;
    }

    private static void collectConjuncts(SqlExpr expr, List<SqlExpr> list) {
        if (expr instanceof SqlAndExpr) {
            SqlAndExpr and = (SqlAndExpr) expr;
            collectConjuncts(and.getLeft(), list);
            collectConjuncts(and.getRight(), list);
        } else if (expr != null) {
            list.add(expr);
        }
    }

    private static String joinTypeName(SqlJoinType joinType) {
        switch (joinType) {
            case JOIN:
                return "INNER";
            case LEFT_JOIN:
                return "LEFT";
            case RIGHT_JOIN:
                return "RIGHT";
            case FULL_JOIN:
                return "FULL";
            default:
                throw CompileErrors.invalidArg("unsupported join type " + joinType);
        }
    }

    // ----------------------------------------------------------------
    // aggregate scanning / grouping
    // ----------------------------------------------------------------

    private static class AggregateScan {
        final Map<SqlAggregateFunction, Integer> aggregates = new IdentityHashMap<>();
        final List<String> outputNames = new ArrayList<>();
        final List<SqlExpr> outputExprs = new ArrayList<>();
    }

    private static class GroupedAggregate {
        final AggregateScan scan;
        final List<String> keyXplExprs = new ArrayList<>();
        final List<String> keySqlTexts = new ArrayList<>();
        final Map<SqlAggregateFunction, Integer> aggregateSlots = new IdentityHashMap<>();
        SqlRowAggregateSpec spec;
        long durationMillis = -1;

        GroupedAggregate(AggregateScan scan) {
            this.scan = scan;
        }

        int keyCount() {
            return keyXplExprs.size();
        }
    }

    private static AggregateScan scanNoAggregate(SqlQuerySelect select) {
        AggregateScan scan = new AggregateScan();
        collectProjections(select, scan);
        return scan;
    }

    private static AggregateScan scanProjections(SqlQuerySelect select) {
        AggregateScan scan = new AggregateScan();
        collectProjections(select, scan);
        // DISTINCT aggregates fail fast (4a#6). Slot indices come from collectAggregates'
        // putIfAbsent (SELECT discovery order) — never re-assigned from the (unordered)
        // IdentityHashMap iteration, so "聚合按 SELECT 序" holds.
        for (SqlAggregateFunction agg : new ArrayList<>(scan.aggregates.keySet())) {
            if (agg.getDistinct()) {
                throw CompileErrors.invalidArg("DISTINCT aggregate is outside the stream SQL "
                        + "v1 subset (4a#6)");
            }
        }
        return scan;
    }

    private static void collectProjections(SqlQuerySelect select, AggregateScan scan) {
        if (select.getProjections() == null || select.getProjections().isEmpty()) {
            throw CompileErrors.invalidArg("SELECT requires an explicit projection list "
                    + "(SELECT * is outside the stream SQL v1 subset)");
        }
        for (SqlProjection p : select.getProjections()) {
            if (p instanceof SqlAllProjection) {
                throw CompileErrors.invalidArg("SELECT * is outside the stream SQL v1 subset: "
                        + "declare the projection list explicitly");
            }
            if (!(p instanceof SqlExprProjection)) {
                throw CompileErrors.invalidArg("projection type " + p.getClass().getSimpleName()
                        + " is outside the stream SQL v1 subset");
            }
            SqlExprProjection proj = (SqlExprProjection) p;
            scan.outputExprs.add(proj.getExpr());
            scan.outputNames.add(outputName(proj));
            collectAggregates(proj.getExpr(), scan.aggregates);
        }
    }

    private static String outputName(SqlExprProjection proj) {
        if (proj.getAlias() != null && proj.getAlias().getAlias() != null) {
            return proj.getAlias().getAlias();
        }
        if (proj.getExpr() instanceof SqlColumnName) {
            return lastSegment((SqlColumnName) proj.getExpr());
        }
        return proj.getExpr().toSqlString().trim();
    }

    private static void collectAggregates(SqlExpr expr, Map<SqlAggregateFunction, Integer> out) {
        if (expr == null)
            return;
        if (expr instanceof SqlAggregateFunction) {
            out.putIfAbsent((SqlAggregateFunction) expr, out.size());
            return;
        }
        if (expr instanceof SqlAndExpr) {
            collectAggregates(((SqlAndExpr) expr).getLeft(), out);
            collectAggregates(((SqlAndExpr) expr).getRight(), out);
        } else if (expr instanceof SqlOrExpr) {
            collectAggregates(((SqlOrExpr) expr).getLeft(), out);
            collectAggregates(((SqlOrExpr) expr).getRight(), out);
        } else if (expr instanceof SqlNotExpr) {
            collectAggregates(((SqlNotExpr) expr).getExpr(), out);
        } else if (expr instanceof SqlUnaryExpr) {
            collectAggregates(((SqlUnaryExpr) expr).getExpr(), out);
        } else if (expr instanceof SqlIsNullExpr) {
            collectAggregates(((SqlIsNullExpr) expr).getExpr(), out);
        } else if (expr instanceof SqlBetweenExpr) {
            SqlBetweenExpr bet = (SqlBetweenExpr) expr;
            collectAggregates(bet.getTest(), out);
            collectAggregates(bet.getBegin(), out);
            collectAggregates(bet.getEnd(), out);
        } else if (expr instanceof SqlInValuesExpr) {
            SqlInValuesExpr in = (SqlInValuesExpr) expr;
            collectAggregates(in.getExpr(), out);
            if (in.getValues() != null)
                in.getValues().forEach(v -> collectAggregates(v, out));
        } else if (expr instanceof SqlBinaryExpr) {
            SqlBinaryExpr bin = (SqlBinaryExpr) expr;
            collectAggregates(bin.getLeft(), out);
            collectAggregates(bin.getRight(), out);
        }
        // leaf scalar nodes (columns/literals) carry no aggregates — nothing to do
    }

    private static boolean hasAggregate(List<SqlProjection> projections) {
        if (projections == null)
            return false;
        for (SqlProjection p : projections) {
            if (p instanceof SqlExprProjection
                    && containsAggregate(((SqlExprProjection) p).getExpr())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAggregate(SqlExpr expr) {
        IdentityHashMap<SqlAggregateFunction, Integer> out = new IdentityHashMap<>();
        collectAggregates(expr, out);
        return !out.isEmpty();
    }

    private static GroupedAggregate buildGroupedAggregate(SqlQuerySelect select,
                                                          AggregateScan scan,
                                                          SqlTableSource table,
                                                          boolean hasTumble) {
        GroupedAggregate grouped = new GroupedAggregate(scan);
        SqlGroupBy groupBy = select.getGroupBy();
        if (groupBy.getItems() == null || groupBy.getItems().isEmpty()) {
            throw CompileErrors.invalidArg("GROUP BY requires at least one key expression");
        }
        for (SqlGroupByItem item : groupBy.getItems()) {
            String text = item.getExpr().toSqlString().trim();
            grouped.keySqlTexts.add(text);
            grouped.keyXplExprs.add(new SqlScalarXplPrinter(singleInputResolver(), null)
                    .print(item.getExpr()));
        }
        if (hasTumble) {
            grouped.durationMillis = EqlParseHelper.intervalDurationMillis(
                    ((SqlTumbleTableSource) table).getInterval());
        }

        List<SqlRowAggregateSpec.AggCall> calls = new ArrayList<>();
        List<SqlAggregateFunction> aggs = new ArrayList<>(scan.aggregates.keySet());
        aggs.sort((x, y) -> Integer.compare(scan.aggregates.get(x), scan.aggregates.get(y)));
        for (SqlAggregateFunction agg : aggs) {
            grouped.aggregateSlots.put(agg, grouped.aggregateSlots.size());
            String argText = (agg.getArgs() == null || agg.getArgs().isEmpty())
                    ? null : agg.getArgs().get(0).toSqlString().trim();
            calls.add(new SqlRowAggregateSpec.AggCall(agg.getName().toLowerCase(), argText,
                    agg.getSelectAll()));
        }
        grouped.spec = new SqlRowAggregateSpec(new ArrayList<>(grouped.keySqlTexts), calls);
        return grouped;
    }

    // ----------------------------------------------------------------
    // resolvers / projection
    // ----------------------------------------------------------------

    private static SqlScalarXplPrinter.ColumnResolver singleInputResolver() {
        return col -> "event['" + lastSegment(col) + "']";
    }

    private static SqlScalarXplPrinter.ColumnResolver joinProjectionResolver(SideRef left,
                                                                             SideRef right) {
        return col -> {
            String owner = qualifier(col);
            if (owner == null) {
                throw CompileErrors.invalidArg("column reference '" + col.getName()
                        + "' over a join must be qualified with the table alias");
            }
            if (owner.equalsIgnoreCase(left.scope))
                return "event.left['" + lastSegment(col) + "']";
            if (owner.equalsIgnoreCase(right.scope))
                return "event.right['" + lastSegment(col) + "']";
            throw CompileErrors.invalidArg("unknown join scope qualifier '" + owner + "'");
        };
    }

    private static SqlScalarXplPrinter.ColumnResolver aggregatedRowResolver(
            GroupedAggregate grouped) {
        return col -> {
            String text = col.toSqlString().trim();
            for (int i = 0; i < grouped.keySqlTexts.size(); i++) {
                if (grouped.keySqlTexts.get(i).equalsIgnoreCase(text)) {
                    return "event[" + i + "]";
                }
            }
            throw CompileErrors.invalidArg("column '" + text
                    + "' must appear in GROUP BY or inside an aggregate");
        };
    }

    private static SqlScalarXplPrinter.AggregateRef aggregateRef(GroupedAggregate grouped) {
        return agg -> {
            Integer slot = grouped.aggregateSlots.get(agg);
            if (slot == null)
                return null;
            return "event[" + (grouped.keyCount() + slot) + "]";
        };
    }

    private static String printWhere(SqlExpr where) {
        return new SqlScalarXplPrinter(singleInputResolver(), null).print(where);
    }

    private static XNode projectionNode(SqlQuerySelect select, AggregateScan scan,
                                        GroupedAggregate grouped, String edgeId, String projId,
                                        SqlScalarXplPrinter.ColumnResolver columns) {
        SqlScalarXplPrinter printer = new SqlScalarXplPrinter(columns,
                grouped == null ? null : aggregateRef(grouped));
        StringBuilder body = new StringBuilder(128);
        body.append("let __r = {");
        for (int i = 0; i < scan.outputExprs.size(); i++) {
            if (i > 0)
                body.append(", ");
            body.append(xlangString(scan.outputNames.get(i))).append(": ")
                    .append(printer.print(scan.outputExprs.get(i)));
        }
        body.append("}; return __r;");
        XNode map = mk(loc(select), "map", "id", projId);
        map.appendChild(textNode(loc(select), "source", body.toString()));
        return map;
    }

    // ----------------------------------------------------------------
    // node builders
    // ----------------------------------------------------------------


    private static XNode mk(SourceLocation loc, String tag, Object... kv) {
        XNode node = XNode.make(loc, tag);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            node.setAttr((String) kv[i], kv[i + 1]);
        }
        return node;
    }

    private static XNode sourceNode(SourceLocation loc, String id, String beanName) {
        return mk(loc, "source", "id", id, "bean", beanName);
    }

    private static XNode timestampsNode(SqlTumbleTableSource tumble, String id) {
        XNode node = mk(loc(tumble), "timestampsAndWatermarks", "id", id);
        node.appendChild(textNode(loc(tumble), "timestampAssigner",
                "return event['" + tumble.getTimeColumn() + "'];"));
        node.appendChild(textNode(loc(tumble), "watermarkGenerator",
                "import io.nop.stream.core.streamrecord.watermark.Watermark;"
                        + " output.emitWatermark(new Watermark(eventTimestamp));"));
        return node;
    }

    private static void appendSink(String lastId, String sinkBean, List<XNode> transforms,
                                   List<XNode> edges) {
        String sinkId = "out";
        transforms.add(mk(null, "sink", "id", sinkId, "bean", sinkBean));
        edges.add(edge(null, "e_out", lastId, sinkId));
    }

    private static XNode edge(SourceLocation loc, String id, String from, String to) {
        return mk(loc, "edge", "id", id, "from", from, "to", to);
    }

    private static XNode textNode(SourceLocation loc, String tag, String text) {
        XNode node = XNode.make(loc, tag);
        node.content(loc, text);
        return node;
    }

    private static SourceLocation loc(Object node) {
        if (node instanceof EqlASTNode) {
            return ((EqlASTNode) node).getLocation();
        }
        return null;
    }

    private static String keyExprText(List<String> keyExprs) {
        if (keyExprs.size() == 1) {
            return keyExprs.get(0);
        }
        return "[" + String.join(", ", keyExprs) + "]";
    }

    /**
     * keyBy over the accumulator row: the composite accumulator is
     * {@code Object[]{ Object[] keys, Object[] subAccs }}, so the partition key reads
     * the keys-array slots — the stored key object is stable across merges (first-seen
     * wins), which the reduce state lookup requires.
     */
    private static String rowHeadKeyExpr(int keyCount) {
        if (keyCount == 1) {
            return "event[0][0]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < keyCount; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append("event[0][").append(i).append(']');
        }
        return sb.append(']').toString();
    }

    private static String xlangString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\')
                sb.append('\\');
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    private static String lastSegment(SqlColumnName col) {
        // the grammar stores the qualifier in owner (SqlQualifiedName); name is the
        // last segment already
        return col.getName();
    }

    private static String qualifier(SqlColumnName col) {
        io.nop.orm.eql.ast.SqlQualifiedName owner = col.getOwner();
        if (owner == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (io.nop.orm.eql.ast.SqlQualifiedName q = owner; q != null; q = q.getNext()) {
            if (sb.length() > 0)
                sb.append('.');
            sb.append(q.getName());
        }
        return sb.toString();
    }

    private static void validateSchema(Map<String, String> schema) {
        if (schema == null)
            return;
        for (Map.Entry<String, String> e : schema.entrySet()) {
            if (!MANAGED_TYPES.contains(e.getValue())) {
                throw CompileErrors.invalidArg("schema column '" + e.getKey() + "' declares type '"
                        + e.getValue() + "' which is outside the D7 managed type closed set "
                        + MANAGED_TYPES);
            }
        }
    }

    private static XNode buildSchemasNode(SourceLocation loc, CompileContext ctx) {
        XNode schemas = XNode.make(loc, "schemas");
        XNode schema = mk(loc, "schema", "id", SCHEMA_ID);
        XNode fields = XNode.make(loc, "fields");
        for (Map.Entry<String, String> e : ctx.schemaFields.entrySet()) {
            fields.appendChild(mk(loc, "field", "name", e.getKey(), "type", e.getValue()));
        }
        schema.appendChild(fields);
        schemas.appendChild(schema);
        return schemas;
    }

    // ----------------------------------------------------------------
    // context
    // ----------------------------------------------------------------

    private static class CompileContext {
        final Map<String, String> schemaFields;
        final List<XNode> strategies = new ArrayList<>();
        final List<XNode> aggregatorEntries = new ArrayList<>();
        final List<XNode> joinSpecs = new ArrayList<>();
        int strategySeq;
        int aggregatorSeq;
        int joinSpecSeq;

        CompileContext(Map<String, String> schemaFields) {
            this.schemaFields = schemaFields == null ? new LinkedHashMap<>() : schemaFields;
        }

        String nextStrategy(String base, long durationMillis) {
            String id = base + (strategySeq++);
            strategies.add(mk(null, "strategy", "strategyId", id, "windowFnId", "tumbling-event-time", "duration", durationMillis + "ms"));
            return id;
        }

        String nextAggregator(String base, SqlRowAggregateSpec spec, boolean withSchema) {
            String id = base + (aggregatorSeq++);
            XNode entry = mk(null, "aggregator", "aggregatorId", id, "fnId", SqlRowAggregateSpec.FN_ID, "expr", spec.toJson());
            if (withSchema) {
                entry.setAttr("schemaId", SCHEMA_ID);
            }
            aggregatorEntries.add(entry);
            return id;
        }

        String nextJoinSpec(String base, String joinType, String leftKeys, String rightKeys) {
            String id = base + (joinSpecSeq++);
            joinSpecs.add(mk(null, "joinSpec", "joinId", id, "joinType", joinType, "leftKeyExprs", leftKeys, "rightKeyExprs", rightKeys));
            return id;
        }
    }

    private static class SideRef {
        final String scope;
        final String tableName;
        final boolean tumble;
        final SqlTableSource source;

        SideRef(String scope, String tableName, boolean tumble, SqlTableSource source) {
            this.scope = scope;
            this.tableName = tableName;
            this.tumble = tumble;
            this.source = source;
        }
    }

    private static class JoinKeys {
        final String leftKeyExprs;
        final String rightKeyExprs;

        JoinKeys(String leftKeyExprs, String rightKeyExprs) {
            this.leftKeyExprs = leftKeyExprs;
            this.rightKeyExprs = rightKeyExprs;
        }
    }
}
