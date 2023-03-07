/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.crud;

import io.nop.xlang.xmeta.IObjMeta;

import java.util.Map;

public class EntityData<T> {
    private final Map<String, Object> validatedData;
    private T entity;
    private final IObjMeta objMeta;
    private boolean recoverDeleted;

    public EntityData(Map<String, Object> validatedData, T entity, IObjMeta objMeta) {
        this.validatedData = validatedData;
        this.entity = entity;
        this.objMeta = objMeta;
    }

    public boolean isRecoverDeleted() {
        return recoverDeleted;
    }

    public void setRecoverDeleted(boolean recoverDeleted) {
        this.recoverDeleted = recoverDeleted;
    }

    public void setEntity(T entity) {
        this.entity = entity;
    }

    public IObjMeta getObjMeta() {
        return objMeta;
    }

    public Map<String, Object> getValidatedData() {
        return validatedData;
    }

    public T getEntity() {
        return entity;
    }
}
