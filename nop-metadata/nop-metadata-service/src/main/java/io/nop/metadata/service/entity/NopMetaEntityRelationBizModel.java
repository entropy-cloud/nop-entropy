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

import io.nop.metadata.biz.INopMetaEntityRelationBiz;
import io.nop.metadata.dao.entity.NopMetaEntityRelation;

@BizModel("NopMetaEntityRelation")
public class NopMetaEntityRelationBizModel extends CrudBizModel<NopMetaEntityRelation> implements INopMetaEntityRelationBiz{
    public NopMetaEntityRelationBizModel(){
        setEntityName(NopMetaEntityRelation.class.getName());
    }
}
