/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.sys.dao.entity._gen._NopSysLock;

import io.nop.sys.dao.entity._gen.NopSysLockPkBuilder;


@BizObjName("NopSysLock")
public class NopSysLock extends _NopSysLock{
    public NopSysLock(){
    }


    public static NopSysLockPkBuilder newPk(){
        return new NopSysLockPkBuilder();
    }

}
