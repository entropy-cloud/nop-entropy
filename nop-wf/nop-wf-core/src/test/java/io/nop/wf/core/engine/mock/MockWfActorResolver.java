package io.nop.wf.core.engine.mock;

import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.IWfActorResolver;

public class MockWfActorResolver implements IWfActorResolver {
    @Override
    public IWfActor resolveUser(String userId) {
        return null;
    }

    @Override
    public IWfActor resolveActor(String actorType, String actorId, String deptId) {
        return null;
    }
}
