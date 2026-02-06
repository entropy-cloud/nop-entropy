/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.auth.dao.entity.NopAuthUserSubstitution;
import io.nop.biz.crud.CrudBizModel;
import io.nop.auth.biz.INopAuthUserSubstitutionBiz;

@BizModel("NopAuthUserSubstitution")
public class NopAuthUserSubstitutionBizModel extends CrudBizModel<NopAuthUserSubstitution> implements INopAuthUserSubstitutionBiz {
    public NopAuthUserSubstitutionBizModel() {
        setEntityName(NopAuthUserSubstitution.class.getName());
    }
}
