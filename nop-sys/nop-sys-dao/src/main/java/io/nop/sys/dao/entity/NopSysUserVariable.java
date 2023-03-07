/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.sys.dao.entity._gen._NopSysUserVariable;

import io.nop.sys.dao.entity._gen.NopSysUserVariablePkBuilder;


@BizObjName("NopSysUserVariable")
public class NopSysUserVariable extends _NopSysUserVariable{
    public NopSysUserVariable(){
    }


    public static NopSysUserVariablePkBuilder newPk(){
        return new NopSysUserVariablePkBuilder();
    }

}
