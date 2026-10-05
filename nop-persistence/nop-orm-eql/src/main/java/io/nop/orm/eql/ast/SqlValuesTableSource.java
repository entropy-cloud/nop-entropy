/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlValuesTableSource;
import io.nop.orm.eql.meta.ISqlSelectionMeta;

/**
 * WI12 Phase 4（plan 38，裁定
 * ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §4）：
 * {@code FROM (VALUES (1),(2)) t(c1)} 表源。手写侧承载 transform 期解析出的表元数据
 * （{@link #resolvedTableMeta}，transform 层经 buildValuesTableMeta 构建列名/类型——
 * 显式别名列清单优先，缺省 C1..Cn H2 惯例；SqlSelect 的 resolvedTableMeta 手写字段
 * 同款分层，不进 xjava 模型）。
 */
public class SqlValuesTableSource extends _SqlValuesTableSource {
    private ISqlSelectionMeta resolvedTableMeta;

    @Override
    public ISqlSelectionMeta getResolvedTableMeta() {
        return resolvedTableMeta;
    }

    public void setResolvedTableMeta(ISqlSelectionMeta resolvedTableMeta) {
        this.resolvedTableMeta = resolvedTableMeta;
    }

    @Override
    public SqlSelect getSourceSelect() {
        // VALUES 表源无内层 select（区别于 SqlSubqueryTableSource）；null = 无
        return null;
    }

    @Override
    public boolean isGeneratedAlias() {
        return alias != null && alias.isGenerated();
    }
}
