/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.mock;

import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.WfUserActorBean;

/**
 * 增强的 MockWfActorResolver，支持 examples 中使用的 StarterManager/StarterDeptManager/role 等 actor。
 * 引擎不通过 selectUser 展开 role 为 user 步骤，因此在 Mock 中将 role/dept 直接解析为 user，
 * 以避免权限检查中对 role actor 的 delegate 检查问题。
 */
public class EnhancedMockWfActorResolver extends MockWfActorResolver {
    @Override
    public IWfActor getManager(IWfActor actor, int upLevel) {
        WfUserActorBean user = new WfUserActorBean();
        user.setActorId("mgr" + upLevel);
        user.setActorName("mgr" + upLevel);
        return user;
    }

    @Override
    public IWfActor getDeptManager(IWfActor actor, int upLevel) {
        WfUserActorBean user = new WfUserActorBean();
        user.setActorId("deptMgr" + upLevel);
        user.setActorName("deptMgr" + upLevel);
        return user;
    }

    @Override
    public IWfActor resolveActor(String actorType, String actorId, String deptId) {
        if (IWfActor.ACTOR_TYPE_ROLE.equals(actorType) || IWfActor.ACTOR_TYPE_DEPT.equals(actorType)) {
            return resolveUser(actorId);
        }
        return super.resolveActor(actorType, actorId, deptId);
    }
}
