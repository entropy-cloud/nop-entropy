/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */


package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaDomainBiz;
import io.nop.metadata.dao.entity.NopMetaDomain;

@BizModel("NopMetaDomain")
public class NopMetaDomainBizModel extends CrudBizModel<NopMetaDomain> implements INopMetaDomainBiz{
    public NopMetaDomainBizModel(){
        setEntityName(NopMetaDomain.class.getName());
    }
}
