/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.message;

import io.nop.api.core.util.ICancellable;

import java.util.function.Consumer;

/**
 * 监听异步消息
 *
 * @param <S>
 */
public interface IMessageSource<S> {
    ICancellable subscribe(Consumer<S> action);
}