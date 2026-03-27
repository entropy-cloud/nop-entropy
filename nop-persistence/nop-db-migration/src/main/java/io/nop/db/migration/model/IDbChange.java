/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.model;

/**
 * 数据库变更操作的通用接口
 * 所有变更类型（CreateTable, AddColumn, Sql 等）都实现此接口
 */
public interface IDbChange {
    String getId();
    String getType();
    void setId(String id);
    void setType(String type);
}
