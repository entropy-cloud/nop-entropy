/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.auth.dao.entity.NopAuthTenant;
import io.nop.biz.crud.CrudBizModel;
import io.nop.auth.biz.INopAuthTenantBiz;

@BizModel("NopAuthTenant")
public class NopAuthTenantBizModel extends CrudBizModel<NopAuthTenant> implements INopAuthTenantBiz {
    public NopAuthTenantBizModel() {
        setEntityName(NopAuthTenant.class.getName());
    }
}
