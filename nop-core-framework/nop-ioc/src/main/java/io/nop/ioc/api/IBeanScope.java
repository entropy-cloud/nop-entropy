/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.api;

import io.nop.core.lang.eval.IEvalScope;

import java.util.Map.Entry;
import java.util.Set;

public interface IBeanScope extends AutoCloseable {
    String SCOPE_TASK = "task";

    IEvalScope getEvalScope();

    IBeanContainerImplementor getContainer();

    /**
     * scope的名称
     *
     * @return
     */
    String getName();

    Set<Entry<String, Object>> entrySet();

    Object get(String name);

    void add(String name, Object bean);

    boolean remove(String name, Object bean);

    void close();
}