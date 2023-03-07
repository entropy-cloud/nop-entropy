/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.context.action;

import io.nop.api.core.util.IOrdered;

/**
 * 类似于aop的interceptor，为action增加附加处理功能
 */
public interface IServiceActionDecorator extends IOrdered {
    IServiceAction decorate(IServiceAction action);
}
