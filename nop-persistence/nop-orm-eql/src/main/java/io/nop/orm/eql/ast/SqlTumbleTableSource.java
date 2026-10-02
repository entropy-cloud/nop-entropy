/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.api.core.exceptions.NopException;
import io.nop.orm.eql.ast._gen._SqlTumbleTableSource;
import io.nop.orm.eql.meta.ISqlSelectionMeta;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_TABLE_SOURCE_NOT_RESOLVED;

/**
 * WI17: TUMBLE(t, INTERVAL) 流时间切片伪表函数表源（D4 裁定，stream-only 目标）。
 * ORM/RDBMS 侧没有对应表元数据——T1 直通裁定下该表源仅流目标可执行，ORM 编译
 * 通道的 resolve 方法 fail-fast（{@code nop.err.eql.table-source-not-resolved}），
 * 不静默近似；toSQL 的 TUMBLE 渲染归 WI20。
 */
public class SqlTumbleTableSource extends _SqlTumbleTableSource {

    @Override
    public SqlSelect getSourceSelect() {
        throw new NopException(ERR_EQL_TABLE_SOURCE_NOT_RESOLVED).loc(getLocation());
    }

    @Override
    public ISqlSelectionMeta getResolvedTableMeta() {
        throw new NopException(ERR_EQL_TABLE_SOURCE_NOT_RESOLVED).loc(getLocation());
    }
}
