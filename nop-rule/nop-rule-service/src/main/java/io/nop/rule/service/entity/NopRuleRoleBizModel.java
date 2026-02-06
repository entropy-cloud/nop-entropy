/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.rule.dao.entity.NopRuleRole;
import io.nop.rule.biz.INopRuleRoleBiz;

@BizModel("NopRuleRole")
public class NopRuleRoleBizModel extends CrudBizModel<NopRuleRole> implements INopRuleRoleBiz {
    public NopRuleRoleBizModel(){
        setEntityName(NopRuleRole.class.getName());
    }
}
