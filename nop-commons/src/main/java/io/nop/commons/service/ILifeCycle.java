/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.service;

/**
 * 一般的服务对象需要实现生命周期接口。 可以从{@link LifeCycleSupport}继承缺省实现。
 */
public interface ILifeCycle {
    void start();

    void stop();
}
