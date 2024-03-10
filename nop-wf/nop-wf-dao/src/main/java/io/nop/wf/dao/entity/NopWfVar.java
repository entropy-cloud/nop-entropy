/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.wf.dao.entity._gen._NopWfVar;

import io.nop.wf.dao.entity._gen.NopWfVarPkBuilder;


@BizObjName("NopWfVar")
public class NopWfVar extends _NopWfVar{
    public NopWfVar(){
    }


    public static NopWfVarPkBuilder newPk(){
        return new NopWfVarPkBuilder();
    }

}
