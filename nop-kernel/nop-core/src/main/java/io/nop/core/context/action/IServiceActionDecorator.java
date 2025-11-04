/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.context.action;

import io.nop.api.core.util.IOrdered;

/**
 * 类似于aop的interceptor，为action增加附加处理功能
 */
public interface IServiceActionDecorator extends IOrdered {
    IServiceAction decorate(IServiceAction action);
}
