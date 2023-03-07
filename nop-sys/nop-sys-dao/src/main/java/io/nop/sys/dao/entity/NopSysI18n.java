/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.sys.dao.entity._gen._NopSysI18n;

import io.nop.sys.dao.entity._gen.NopSysI18nPkBuilder;


@BizObjName("NopSysI18n")
public class NopSysI18n extends _NopSysI18n{
    public NopSysI18n(){
    }


    public static NopSysI18nPkBuilder newPk(){
        return new NopSysI18nPkBuilder();
    }

}
