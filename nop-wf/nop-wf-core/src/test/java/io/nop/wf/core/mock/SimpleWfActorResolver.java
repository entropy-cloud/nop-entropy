package io.nop.wf.core.mock;

import io.nop.api.core.util.Guard;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.IWfActorResolver;
import io.nop.wf.api.actor.WfActorBean;
import io.nop.wf.api.actor.WfUserActorBean;

/**
 * wf-core 测试专用参与者解析器，仿照 nop-wf-service 测试中的 MockWfActorResolver。
 * 所有 actorType 均可解析，保证引擎语义测试不依赖外部组织结构。
 */
public class SimpleWfActorResolver implements IWfActorResolver {
    @Override
    public IWfActor resolveUser(String userId) {
        WfUserActorBean user = new WfUserActorBean();
        user.setActorId(userId);
        user.setActorName(userId);
        return user;
    }

    @Override
    public IWfActor resolveActor(String actorType, String actorId, String deptId) {
        if (actorType.equals(IWfActor.ACTOR_TYPE_USER)) {
            return resolveUser(actorId);
        }

        Guard.checkArgument(actorType.indexOf(':') < 0);

        WfActorBean actor = new WfActorBean();
        actor.setActorType(actorType);
        actor.setActorId(actorId);
        actor.setDeptId(deptId);
        return actor;
    }

    @Override
    public IWfActor getManager(IWfActor actor, int upLevel) {
        return null;
    }

    @Override
    public IWfActor getDeptManager(IWfActor actor, int upLevel) {
        return null;
    }
}
