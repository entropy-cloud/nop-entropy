/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import io.nop.orm.model.IEntityModel;

/**
 * 如果使用eql语句来更新数据，或者查询数据时返回空集，则有可能IOrmInterceptor没有被触发， 这里提供的daoListener可以确保每次数据库访问，无论是否返回了数据都会被触发。这一机制在自动化测试框架中被使用。
 */
public interface IOrmDaoListener {
    void onRead(IEntityModel entityModel);

    void onUpdate(IEntityModel entityModel);

    void onDelete(IEntityModel entityModel);

    void onSave(IEntityModel entityModel);
}
