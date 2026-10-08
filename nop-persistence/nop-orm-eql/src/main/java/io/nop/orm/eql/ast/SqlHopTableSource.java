/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.api.core.exceptions.NopException;
import io.nop.orm.eql.ast._gen._SqlHopTableSource;
import io.nop.orm.eql.meta.ISqlSelectionMeta;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_TABLE_SOURCE_NOT_RESOLVED;

/**
 * WI26: HOP(t, slide, size) 滑动窗口伪表函数表源（D4 形态补全，Flink TVF 参数序，
 * stream-only 目标）。ORM/RDBMS 侧没有对应表元数据——T1 直通裁定下该表源仅流目标
 * 可执行，ORM 编译通道的 resolve 方法 fail-fast
 * （{@code nop.err.eql.table-source-not-resolved}），不静默近似；toSQL 的 HOP 渲染
 * 与 TUMBLE 同为逐字回显。
 */
public class SqlHopTableSource extends _SqlHopTableSource {

    @Override
    public SqlSelect getSourceSelect() {
        throw new NopException(ERR_EQL_TABLE_SOURCE_NOT_RESOLVED).loc(getLocation());
    }

    @Override
    public ISqlSelectionMeta getResolvedTableMeta() {
        throw new NopException(ERR_EQL_TABLE_SOURCE_NOT_RESOLVED).loc(getLocation());
    }
}
