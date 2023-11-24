package io.nop.wf.core.engine;

import io.nop.api.core.util.Guard;
import io.nop.wf.api.actor.IWfActor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WfActorWithWeight {
    private final IWfActor actor;
    private final int voteWeight;

    public WfActorWithWeight(IWfActor actor, int voteWeight) {
        this.actor = Guard.notNull(actor, "actor");
        this.voteWeight = voteWeight;
    }

    public IWfActor getActor() {
        return actor;
    }

    public int getVoteWeight() {
        return voteWeight;
    }

    public static List<WfActorWithWeight> toAssignment(List<IWfActor> actors, int voteWeight) {
        if (actors == null || actors.isEmpty())
            return Collections.emptyList();

        List<WfActorWithWeight> ret = new ArrayList<>(actors.size());
        for (IWfActor actor : actors) {
            ret.add(new WfActorWithWeight(actor, voteWeight));
        }
        return ret;
    }
}
