/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.dyn.dao.entity._gen._NopDynModuleDep;

import io.nop.dyn.dao.entity._gen.NopDynModuleDepPkBuilder;


@BizObjName("NopDynModuleDep")
public class NopDynModuleDep extends _NopDynModuleDep{


    public static NopDynModuleDepPkBuilder newPk(){
        return new NopDynModuleDepPkBuilder();
    }

}
