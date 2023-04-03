package io.nop.auth.service.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;

import javax.inject.Inject;
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
