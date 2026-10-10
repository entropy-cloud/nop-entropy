/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/nop-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlUpsert;
import io.nop.orm.eql.compile.ISqlTableSourceSupport;

/**
 * Plan 2380：{@code upsert} 语句的手写 wrapper。
 *
 * <p>codegen 只会产出抽象的 {@code _SqlUpsert}（{@code templates/ast/{astNode.name}.java.xgen}），
 * 而 {@link SqlStatement#getStatementKind()} 仍是抽象方法 ⇒ 必须手写本类提供实现。
 * 结构与 {@link SqlInsert} 同款：声明语句种类 + 实现表源支持接口 + 承载解析期补入的表源。
 *
 * <p>非 {@code _} 前缀文件不会被 codegen 强制覆盖（只有 {@code _} 前缀与 {@code /_gen/} 下的文件
 * 才是每次覆盖面），因此本类的手写内容可持续维护。
 */
public class SqlUpsert extends _SqlUpsert implements ISqlTableSourceSupport {
    private SqlSingleTableSource resolvedTableSource;

    @Override
    public SqlStatementKind getStatementKind() {
        return SqlStatementKind.UPSERT;
    }

    @Override
    public SqlSingleTableSource getResolvedTableSource() {
        return resolvedTableSource;
    }

    public void setResolvedTableSource(SqlSingleTableSource resolvedTableSource) {
        this.resolvedTableSource = resolvedTableSource;
    }
}