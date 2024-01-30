/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

public enum OrmEntityState {
    /**
     * 新建实体，尚未与session关联
     */
    TRANSIENT,

    /**
     * 新建实体，暂存在session中，尚未执行sql插入语句
     */
    SAVING,

    /**
     * 新建代理对象，未从数据库中装载
     */
    PROXY,

    /**
     * 内存中的实体对象对应数据库中的一条记录
     */
    MANAGED,

    /**
     * proxy对象装载数据失败，标记内存中的对象为missing状态
     */
    MISSING,

    DELETING,

    /**
     * 成功从数据库中删除记录后，内存中的对象标记为deleted
     */
    DELETED;

    public boolean isUnsaved() {
        return this == TRANSIENT || this == SAVING;
    }

    public boolean isProxy() {
        return this == PROXY;
    }

    public boolean isAllowLoad() {
        return this == PROXY || this == MANAGED || this == DELETING;
    }

    public boolean isGone() {
        return this == MISSING || this == DELETED || this == DELETING;
    }

    public boolean isManaged() {
        return this == MANAGED;
    }

    public boolean isTransient() {
        return this == TRANSIENT;
    }

    public boolean isSaving() {
        return this == SAVING;
    }

    public boolean isDeleting() {
        return this == DELETING;
    }

    public boolean isDeleted() {
        return this == DELETED;
    }

    public boolean isMissing() {
        return this == MISSING;
    }
}