/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

public enum SqlStatementKind {
    // Plan 2307 V4: COMMIT / ROLLBACK 保留在枚举中，但 EqlCompiler 对其 fail-fast
    // （nop.err.eql.not-support-transaction-statement），不再是「编译出空字符串」的静默无操作。
    SELECT, INSERT, UPDATE, DELETE, DDL, COMMIT, ROLLBACK,
}
