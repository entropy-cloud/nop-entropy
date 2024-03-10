/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.engine;

import io.nop.api.core.util.Guard;
import io.nop.wf.api.actor.IWfActor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WfActorWithWeight {
    private final IWfActor actor;
    private final String actorModelId;
    private final int voteWeight;

    public WfActorWithWeight(IWfActor actor, String actorModelId, int voteWeight) {
        this.actor = Guard.notNull(actor, "actor");
        this.actorModelId = actorModelId;
        this.voteWeight = voteWeight;
    }

    public WfActorWithWeight replaceActor(IWfActor actor) {
        return new WfActorWithWeight(actor, actorModelId, voteWeight);
    }

    public String getActorType() {
        return actor.getActorType();
    }

    public String getActorId() {
        return actor.getActorId();
    }

    public boolean isUser(String userId) {
        return actor.isUser(userId);
    }

    public String getActorModelId() {
        return actorModelId;
    }

    public IWfActor getActor() {
        return actor;
    }

    public int getVoteWeight() {
        return voteWeight;
    }

    public static List<WfActorWithWeight> toAssignment(List<IWfActor> actors, String actorModelId,
                                                       int voteWeight) {
        if (actors == null || actors.isEmpty())
            return Collections.emptyList();

        List<WfActorWithWeight> ret = new ArrayList<>(actors.size());
        for (IWfActor actor : actors) {
            ret.add(new WfActorWithWeight(actor, actorModelId, voteWeight));
        }
        return ret;
    }
}
