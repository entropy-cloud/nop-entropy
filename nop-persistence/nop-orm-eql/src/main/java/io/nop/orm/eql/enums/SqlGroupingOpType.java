/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.enums;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

/**
 * WI7b（plan 37，roadmap EC ①）：GROUP BY 位置 grouping 操作符类型。
 *
 * <p>与 {@link SqlWindowFrameType} 同款先例（enums 包 + fromText 大小写不敏感工厂）。
 * 仅 ROLLUP/CUBE 两值——GROUPING SETS 为 roadmap ledger 永久否决项（MySQL/MariaDB 全版本
 * 均无），不设第三值；successor 触发条件见裁定文档 §3.6。
 */
public enum SqlGroupingOpType {
    ROLLUP, CUBE;

    @StaticFactoryMethod
    public static SqlGroupingOpType fromText(String text) {
        for (SqlGroupingOpType value : values()) {
            if (value.name().equalsIgnoreCase(text))
                return value;
        }
        return null;
    }
}
