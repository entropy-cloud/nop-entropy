/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.enums;

public enum SqlJoinType {
    JOIN("join"), LEFT_JOIN("left join"), RIGHT_JOIN("right join"), FULL_JOIN("full join");

    private final String text;

    SqlJoinType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
