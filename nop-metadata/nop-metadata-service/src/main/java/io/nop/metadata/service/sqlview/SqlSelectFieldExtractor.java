package io.nop.metadata.service.sqlview;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.SqlAllProjection;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlSelect;
import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.SqlStatement;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.eql.parse.EqlASTParser;

import java.util.ArrayList;
import java.util.List;

/**
 * SELECT 字段解析器（架构基线 §4.2.1）：对 tableType=sql 的视图 sourceSql 做纯语法解析，
 * 从 SELECT 输出列抽取字段名/别名，返回 {@link SqlViewField} 列表。
 *
 * <p>解析器选型（D1）：复用平台 {@code nop-orm-eql} 的 {@link EqlASTParser}，
 * 调用 {@code parseFromText(text)} 做纯语法 AST 解析（不绑定 ORM session），与 §2.6.1 血缘
 * {@code SqlSourceTableExtractor} 同一解析器、同一无 session 绑定模式。{@code nop-orm-eql}
 * 经 {@code nop-orm}（{@code nop-metadata-dao} 依赖它）已传递可用，无需新增 pom 依赖。
 *
 * <p>字段名解析策略（alias 优先）：{@code SqlExprProjection} 优先取别名
 * （{@code proj.getAlias().getAlias()}）；无别名且 {@code proj.getExpr()} 为 {@link SqlColumnName}
 * 时取 {@code getName()}；无别名且为表达式列时标记 {@code <expr_N>}（不静默跳过）。
 *
 * <p>显式失败路径（不静默返回空、不伪造）：
 * <ul>
 *   <li>SQL 为空 → {@link #ERR_SQL_VIEW_SQL_EMPTY}</li>
 *   <li>SQL 不可解析 → {@link #ERR_SQL_VIEW_PARSE_FAILED}</li>
 *   <li>多语句（{@code ;} 分隔，{@code statements.size()!=1}）→ {@link #ERR_SQL_VIEW_MULTI_STATEMENT}</li>
 *   <li>非 SELECT 顶层语句 → {@link #ERR_SQL_VIEW_NOT_SELECT}</li>
 *   <li>通配符 {@code *}/{@code t.*}（{@link SqlAllProjection}，纯 AST 无法展开）→ {@link #ERR_SQL_VIEW_WILDCARD_NOT_SUPPORTED}</li>
 * </ul>
 *
 * <p>CTE / UNION 支持：{@link SqlSelectWithCte}（{@code WITH ... SELECT}）通过钻入 {@code getSelect()}
 * 取内层 projections；{@code SqlUnionSelect.getProjections()} 委托 firstSelect（取最左侧 SELECT 输出列）。
 *
 * <p>类型获取：首版（方案 A）不取类型，{@code SqlViewField.type} 恒为 null（不伪造）。LIMIT 0 经
 * ResultSetMetaData 取类型（方案 B）为 follow-up。
 *
 * <p>本解析器无状态、无目录依赖（纯文本→字段列表），不可解析路径抛 {@link NopException}。
 */
public class SqlSelectFieldExtractor {

    static final ErrorCode ERR_SQL_VIEW_SQL_EMPTY =
            ErrorCode.define("metadata.sql-empty", "Source sql is empty", "sql");
    static final ErrorCode ERR_SQL_VIEW_PARSE_FAILED =
            ErrorCode.define("metadata.sql-parse-failed", "Failed to parse source sql", "sql");
    static final ErrorCode ERR_SQL_VIEW_MULTI_STATEMENT =
            ErrorCode.define("metadata.sql-multi-statement",
                    "Sql view source must be a single SELECT statement, but got {count} statements", "count", "sql");
    static final ErrorCode ERR_SQL_VIEW_NOT_SELECT =
            ErrorCode.define("metadata.sql-not-select",
                    "Sql view source must be a SELECT statement, but got {statementKind}", "statementKind", "sql");
    static final ErrorCode ERR_SQL_VIEW_WILDCARD_NOT_SUPPORTED =
            ErrorCode.define("metadata.sql-wildcard-not-supported",
                    "Wildcard projection (* or t.*) is not supported in sql view source; "
                            + "please expand to explicit columns (pure AST parse cannot resolve wildcard)",
                    "sql");

    private final EqlASTParser parser = new EqlASTParser();

    /**
     * 解析 SQL 文本，抽取 SELECT 输出列字段列表。
     *
     * @param sql SQL 文本（tableType=sql 的视图定义 sourceSql）
     * @return 字段列表（顺序与 SELECT 输出列一致）；首版每个字段的 {@code type} 为 null（方案 A，不伪造）
     * @throws NopException 当 SQL 为空、不可解析、多语句、非 SELECT、或含通配符时（不静默返回空列表）
     */
    public List<SqlViewField> extract(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new NopException(ERR_SQL_VIEW_SQL_EMPTY).param("sql", sql);
        }

        SqlProgram program;
        try {
            program = parser.parseFromText(null, sql);
        } catch (Exception e) {
            throw new NopException(ERR_SQL_VIEW_PARSE_FAILED).param("sql", sql).cause(e);
        }
        if (program == null || program.getStatements() == null || program.getStatements().isEmpty()) {
            throw new NopException(ERR_SQL_VIEW_PARSE_FAILED).param("sql", sql);
        }

        List<SqlStatement> statements = program.getStatements();
        if (statements.size() != 1) {
            throw new NopException(ERR_SQL_VIEW_MULTI_STATEMENT)
                    .param("count", statements.size()).param("sql", sql);
        }

        SqlStatement stmt = statements.get(0);
        if (stmt.getStatementKind() != SqlStatementKind.SELECT) {
            throw new NopException(ERR_SQL_VIEW_NOT_SELECT)
                    .param("statementKind", String.valueOf(stmt.getStatementKind())).param("sql", sql);
        }

        List<SqlProjection> projections = resolveProjections(stmt);
        return toFields(projections, sql);
    }

    /**
     * 从顶层 SELECT 语句取 projections。{@link SqlSelectWithCte} 不继承 {@link SqlSelect}，
     * 须钻入 {@code getSelect()} 取内层 select 的 projections（CTE 支持裁定，§4.2.1）。
     * {@link SqlSelect}（含 {@code SqlQuerySelect} / {@code SqlUnionSelect}）直接取 projections
     * （UNION 委托 firstSelect，由 AST 实现）。
     */
    private List<SqlProjection> resolveProjections(SqlStatement stmt) {
        if (stmt instanceof SqlSelectWithCte) {
            SqlSelect inner = ((SqlSelectWithCte) stmt).getSelect();
            if (inner == null || inner.getProjections() == null) {
                return new ArrayList<>();
            }
            return inner.getProjections();
        }
        if (stmt instanceof SqlSelect) {
            List<SqlProjection> projections = ((SqlSelect) stmt).getProjections();
            return projections == null ? new ArrayList<>() : projections;
        }
        // getStatementKind()==SELECT 但非 SqlSelect/SqlSelectWithCte 子类——不可达，显式失败而非静默
        throw new NopException(ERR_SQL_VIEW_PARSE_FAILED)
                .param("sql", "unhandled SELECT statement class: " + stmt.getClass().getName());
    }

    /** projections → fields（alias 优先 / 列名次之 / 表达式标记 &lt;expr_N&gt;）。 */
    private List<SqlViewField> toFields(List<SqlProjection> projections, String sql) {
        // 注意：平台 EqlASTParser 对裸 `SELECT *` 会静默丢弃通配符并返回空 projections 列表
        //（已实地验证：`SELECT * FROM t` → projections.size()==0；`SELECT t.* FROM t` → 1 SqlAllProjection）。
        // 一条合法的显式列 SELECT 解析后必有 ≥1 个 projection，故空列表的唯一现实成因就是被丢弃的裸 `*`。
        // 依 §4.2.1 通配符裁定（显式失败、不静默跳过），此处对空 projections 显式失败。
        if (projections.isEmpty()) {
            throw new NopException(ERR_SQL_VIEW_WILDCARD_NOT_SUPPORTED).param("sql", sql);
        }
        List<SqlViewField> fields = new ArrayList<>(projections.size());
        int exprIndex = 0;
        for (SqlProjection proj : projections) {
            if (proj instanceof SqlAllProjection) {
                // 通配符 t.* 纯 AST 无法展开，显式失败（不静默跳过、不伪造）
                throw new NopException(ERR_SQL_VIEW_WILDCARD_NOT_SUPPORTED).param("sql", sql);
            }
            if (proj instanceof SqlExprProjection) {
                SqlViewField field = toField((SqlExprProjection) proj, exprIndex);
                // 表达式标记按表达式列自身序号编号（仅表达式列递增，便于 UI 引用）
                if (field.getName().startsWith("<expr_")) {
                    exprIndex++;
                }
                fields.add(field);
            } else {
                // 未知 projection 子类——显式失败而非静默跳过
                throw new NopException(ERR_SQL_VIEW_PARSE_FAILED)
                        .param("sql", sql)
                        .cause(new IllegalStateException(
                                "unhandled projection class: " + proj.getClass().getName()));
            }
        }
        return fields;
    }

    /** 单个 ExprProjection → field：alias 优先，列名次之，表达式标记兜底。exprIndex 为当前表达式列序号。 */
    private SqlViewField toField(SqlExprProjection proj, int exprIndex) {
        SqlAlias aliasNode = proj.getAlias();
        if (aliasNode != null && aliasNode.getAlias() != null && !aliasNode.getAlias().isEmpty()) {
            // alias 优先：输出名 = alias
            return new SqlViewField(aliasNode.getAlias(), aliasNode.getAlias(), null);
        }
        if (proj.getExpr() instanceof SqlColumnName) {
            // 无别名且为列引用：输出名 = 列名
            String colName = ((SqlColumnName) proj.getExpr()).getName();
            if (colName == null || colName.isEmpty()) {
                // 列名为空——不可达（SqlColumnName.getName mandatory），显式失败
                throw new NopException(ERR_SQL_VIEW_PARSE_FAILED)
                        .param("sql", "empty column name in projection");
            }
            return new SqlViewField(colName, null, null);
        }
        // 无别名且为表达式列：标记 <expr_N>（N 为表达式列自身序号，不静默跳过、不返回空名）
        return new SqlViewField("<expr_" + (exprIndex + 1) + ">", null, null);
    }
}
