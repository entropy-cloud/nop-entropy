/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.core.context.IServiceContext;

/**
 * 延迟执行的命令接口。OrmEntityCopier 产出 IDelayedAction 而非纯数据包，
 * 调用方只需按 order 排序后调用 execute 即可，无需了解内部属性。
 */
public interface IDelayedAction {
    /**
     * 执行顺序，值小的先执行
     */
    int getOrder();

    /**
     * 执行延迟操作
     */
    void execute(IServiceContext context);
}
