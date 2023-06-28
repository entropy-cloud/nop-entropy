/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.impl;

import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.xlang.xmeta.impl._gen._ObjPropAuthModel;

public class ObjPropAuthModel extends _ObjPropAuthModel {
    public ObjPropAuthModel() {

    }

    public ActionAuthMeta toActionAuthMeta() {
        return new ActionAuthMeta(getRoles(), getPermissions());
    }
}
