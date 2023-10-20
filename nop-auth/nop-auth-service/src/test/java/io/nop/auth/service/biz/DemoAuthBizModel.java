/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;

import jakarta.inject.Inject;
import java.util.Arrays;

@BizModel("DemoAuth")
public class DemoAuthBizModel {

    @Inject
    IDaoProvider daoProvider;

    @BizMutation
    public String testFlushError() {
        NopAuthRole role = new NopAuthRole();
        role.setRoleName("test123");
        role.setRoleId("test123");
        // 长度超长，flush的时候会抛出异常
        role.setRelatedUserIdList(Arrays.asList(StringHelper.repeat("a", 1024)));

        daoProvider.daoFor(NopAuthRole.class).saveEntity(role);
        return role.orm_idString();
    }
}
