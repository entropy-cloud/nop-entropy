/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.auth.dao.entity._gen._NopAuthExtLogin;

import io.nop.auth.dao.entity._gen.NopAuthExtLoginPkBuilder;


@BizObjName("NopAuthExtLogin")
public class NopAuthExtLogin extends _NopAuthExtLogin{
    public NopAuthExtLogin(){
    }


    public static NopAuthExtLoginPkBuilder newPk(){
        return new NopAuthExtLoginPkBuilder();
    }

}
