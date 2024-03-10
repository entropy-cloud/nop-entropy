/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.annotations.core.Internal;

public interface IOrmObject {
    boolean orm_readonly();

    void orm_readonly(boolean readonly);

    /**
     * 是否属性已经被修改，需要与数据库同步
     */
    boolean orm_dirty();

    /**
     * 当实体修改后的值已经被更新到数据库之后，引擎调用此函数来清除dirty标记
     */
    void orm_clearDirty();

    /**
     * 是否已经从数据库加载数据。session.load(id)函数返回的仅仅是一个延迟加载的proxy对象，当实际访问对象属性时才会从数据库加载
     */
    boolean orm_proxy();

    /**
     * 放弃实体上已经进行的修改，恢复所有被修改的属性值，并清除dirty标记
     */
    void orm_reset();

    /**
     * 是否与session绑定
     *
     * @return
     */
    boolean orm_attached();

    @Internal
    IOrmEntityEnhancer orm_enhancer();
}