
package io.nop.metadata.service.lineage;

import io.nop.api.core.exceptions.NopException;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlCteStatement;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.parse.EqlASTParser;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * SQL 源表抽取器（架构基线 §2.6.1）：对任意用户 SQL 做纯语法解析，从 FROM/JOIN（含子查询、CTE、UNION）
 * 抽取所有被引用的表名，返回 {@link SqlTableReference} 列表。
 *
 * <p>解析器选型（设计决策 D1）：复用平台 {@code nop-orm-eql} 的 {@link EqlASTParser}，
 * 调用 {@code parseFromText(text)} 做纯语法 AST 解析（不绑定 ORM session——session 绑定的
 * {@code resolvedTableMeta} 解析是独立 compile 阶段，此处不调用）。{@code nop-orm-eql}
 * 经 {@code nop-orm}（{@code nop-metadata-dao} 依赖它）已传递可用，无需新增 pom 依赖。
 *
 * <p>本抽取器无目录依赖（纯文本→表名），表名与目录 {@code NopMetaTable.tableName} 的匹配在 BizModel 层做。
 *
 * <p>限制（显式记录）：不展开 CTE 体（只排除 CTE 名引用，CTE 体内的源表仍被收集）、子查询别名、动态 SQL。
 * SQL 无法解析时抛 {@link NopException}，由调用方收集到 errors 列表（不静默返回空列表）。
 */
public class SqlSourceTableExtractor {


    private final EqlASTParser parser = new EqlASTParser();

    /**
     * 解析 SQL 文本，抽取所有被引用的表名。
     *
     * <p><b>去重（AR-07）</b>：按 {@code fullName}（schema-qualified 名）去重——同一 fullName 的多次引用
     * 只算一条边。跨 schema 同名表（{@code dbo.users} 与 {@code sales.users}）的 fullName 不同，各自独立产出一条边，
     * 不再因 simpleName 相同而塌缩。残留歧义：同一 fullName 经不同别名引用仍为一条边（语义正确——指向同一物理表）。
     *
     * <p><b>CTE 排除（AR-08）</b>：WITH 子句声明的 CTE 名被收集并排除——匹配 CTE 名的
     * {@link SqlSingleTableSource} 不作为物理源表上报（符合 SQL 作用域语义：CTE 名遮蔽同名物理表时，
     * CTE 名被排除，物理表引用不补回）。CTE 体内部的源表仍被正常收集。
     *
     * @param sql SQL 文本（tableType=sql 的视图定义 sourceSql）
     * @return 去重后的表引用列表（按首次出现顺序）
     * @throws NopException 当 SQL 为空或无法解析时（不静默返回空列表）
     */
    public List<SqlTableReference> extract(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_SQL_EMPTY).param("sql", sql);
        }

        SqlProgram program;
        try {
            program = parser.parseFromText(null, sql);
        } catch (Exception e) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_SQL_PARSE_FAILED, e).param("sql", sql);
        }
        if (program == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_SQL_PARSE_FAILED).param("sql", sql);
        }

        // 预遍历：收集所有 WITH 子句声明的 CTE 名（AR-08）。CTE 名引用出现在 FROM/JOIN 时不应被报为物理源表。
        Set<String> cteNames = collectCteNames(program);

        // 递归遍历整个 AST，收集所有 SqlSingleTableSource 的表名。子查询（SqlSubqueryTableSource）
        // 不直接持有表名，但其内层 select 的表名会在递归遍历中被收集。
        Set<String> seen = new LinkedHashSet<>();
        List<SqlTableReference> refs = new ArrayList<>();
        walk(program, node -> {
            if (node instanceof SqlSingleTableSource) {
                SqlTableName tableName = ((SqlSingleTableSource) node).getTableName();
                if (tableName == null) {
                    return;
                }
                String simple = tableName.getName();
                if (simple == null || simple.isEmpty()) {
                    return;
                }
                // CTE 名排除（AR-08）：匹配 CTE 名的引用不上报为物理源表
                if (cteNames.contains(simple.toLowerCase(Locale.ROOT))) {
                    return;
                }
                String full = tableName.getFullName();
                if (full == null || full.isEmpty()) {
                    full = simple;
                }
                // 去重（AR-07）：按 fullName（schema-qualified 名）去重——跨 schema 同名表的 fullName 不同，
                // 各自独立产出一条边，不再因 simpleName 相同而塌缩。
                if (seen.add(full)) {
                    refs.add(new SqlTableReference(full, simple));
                }
            }
        });
        return refs;
    }

    /**
     * 预遍历 AST 收集所有 WITH 子句声明的 CTE 名（lowercase）。CTE 名在 SQL 作用域内遮蔽同名物理表，
     * 故匹配 CTE 名的 {@link SqlSingleTableSource} 不作为物理源表上报。
     */
    private Set<String> collectCteNames(EqlASTNode node) {
        Set<String> cteNames = new HashSet<>();
        walk(node, n -> {
            if (n instanceof SqlSelectWithCte) {
                List<SqlCteStatement> ctes = ((SqlSelectWithCte) n).getWithCtes();
                if (ctes != null) {
                    for (SqlCteStatement cte : ctes) {
                        if (cte != null && cte.getName() != null) {
                            cteNames.add(cte.getName().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        });
        return cteNames;
    }

    /** 递归深度优先遍历 AST 节点树。 */
    private void walk(EqlASTNode node, Consumer<EqlASTNode> consumer) {
        if (node == null) {
            return;
        }
        consumer.accept(node);
        node.forEachChild(child -> walk(child, consumer));
    }
}
