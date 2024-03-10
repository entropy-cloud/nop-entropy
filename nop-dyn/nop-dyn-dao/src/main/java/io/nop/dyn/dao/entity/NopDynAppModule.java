/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.dyn.dao.entity._gen._NopDynAppModule;

import io.nop.dyn.dao.entity._gen.NopDynAppModulePkBuilder;


@BizObjName("NopDynAppModule")
public class NopDynAppModule extends _NopDynAppModule{


    public static NopDynAppModulePkBuilder newPk(){
        return new NopDynAppModulePkBuilder();
    }

}
