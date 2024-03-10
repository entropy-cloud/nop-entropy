/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.lang;

/**
 * 监听对象创建和销毁
 *
 * @param <T>
 */
public interface ICreationListener<T> {

    void onCreated(T object);

    void onDestroyed(T object);
}