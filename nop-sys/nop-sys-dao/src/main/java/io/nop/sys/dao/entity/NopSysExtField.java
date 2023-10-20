/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.sys.dao.entity._gen._NopSysExtField;

import io.nop.sys.dao.entity._gen.NopSysExtFieldPkBuilder;


@BizObjName("NopSysExtField")
public class NopSysExtField extends _NopSysExtField{
    public NopSysExtField(){
    }


    public static NopSysExtFieldPkBuilder newPk(){
        return new NopSysExtFieldPkBuilder();
    }

}
