/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.sql;

import io.nop.commons.type.StdSqlType;

import java.io.Serializable;

/**
 * 用于组装函数表达式的简单语法树结构。不从ASTNode语法树节点继承，可以是简单的文本片段
 *
 * @author canonical_entropy@163.com
 */
public interface ISqlExpr extends Serializable {

    default StdSqlType getStdSqlType() {
        return StdSqlType.ANY;
    }

    void appendTo(SQL.SqlBuilder sb);
}