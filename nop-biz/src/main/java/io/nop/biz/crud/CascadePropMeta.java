/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.crud;

import io.nop.xlang.xmeta.IObjPropMeta;

public class CascadePropMeta {
    private final IObjPropMeta propMeta;
    private final boolean cascadeDelete;
    private final String refBizObjName;
    private final boolean toMany;

    public CascadePropMeta(IObjPropMeta propMeta, boolean cascadeDelete, String refBizObjName, boolean toMany) {
        this.propMeta = propMeta;
        this.cascadeDelete = cascadeDelete;
        this.refBizObjName = refBizObjName;
        this.toMany = toMany;
    }

    public String getPropName() {
        return propMeta.getName();
    }

    public IObjPropMeta getPropMeta() {
        return propMeta;
    }

    public boolean isCascadeDelete() {
        return cascadeDelete;
    }

    public String getRefBizObjName() {
        return refBizObjName;
    }

    public boolean isToMany() {
        return toMany;
    }
}
