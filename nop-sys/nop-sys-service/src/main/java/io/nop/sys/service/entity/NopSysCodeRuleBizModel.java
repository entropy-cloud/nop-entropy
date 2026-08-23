/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.sys.biz.INopSysCodeRuleBiz;
import io.nop.sys.dao.coderule.SysCodeRuleGenerator;
import io.nop.sys.dao.entity.NopSysCodeRule;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

@BizModel("NopSysCodeRule")
public class NopSysCodeRuleBizModel extends CrudBizModel<NopSysCodeRule> implements INopSysCodeRuleBiz {
    /**
     * 编码规则变更后失效生成器规则缓存（codePattern/seqName修改即时生效）。
     */
    @Inject
    @Nullable
    protected SysCodeRuleGenerator codeRuleGenerator;

    public NopSysCodeRuleBizModel(){
        setEntityName(NopSysCodeRule.class.getName());
    }

    @BizAction
    @Override
    protected void afterEntityChange(@Name("entity") NopSysCodeRule entity, @Name("action") String action,
                                     IServiceContext context) {
        super.afterEntityChange(entity, action, context);
        if (codeRuleGenerator != null)
            codeRuleGenerator.clearCache();
    }
}
