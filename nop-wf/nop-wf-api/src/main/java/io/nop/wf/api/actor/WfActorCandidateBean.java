/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.api.actor;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.util.Collections;
import java.util.List;

@DataBean
public class WfActorCandidateBean {
    private IWfActor actor;
    private boolean selectUser;
    private boolean assignForUser;
    private int voteWeight;

    private String actorModelId;
    private List<? extends IWfActor> users;

    public WfActorCandidateBean(IWfActor actor, boolean selectUser, String actorModelId,
                                int voteWeight, boolean assignForUser) {
        this.actor = actor;
        this.selectUser = selectUser;
        this.assignForUser = assignForUser;
        this.actorModelId = actorModelId;
        this.voteWeight = voteWeight;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<? extends IWfActor> getUsers() {
        if (users == null) {
            users = actor.getUsers();
        }
        return users;
    }

    public boolean isAssignForUser() {
        return assignForUser;
    }

    public void setAssignForUser(boolean assignForUser) {
        this.assignForUser = assignForUser;
    }

    public WfActorCandidateBean() {

    }

    public String getActorModelId() {
        return actorModelId;
    }

    public IWfActor getActor() {
        return actor;
    }

    public void setActor(IWfActor actor) {
        this.actor = actor;
    }

    public boolean isSelectUser() {
        return selectUser;
    }

    public void setSelectUser(boolean selectUser) {
        this.selectUser = selectUser;
    }

    public String toString() {
        return "WfActorCandidate[actor=" + actor + ",selectUser=" + selectUser + "]";
    }

    public boolean containsUser(String userId) {
        return actor.containsUser(userId);
    }

    public boolean containsActor(IWfActor actor) {
        return this.actor.isSame(actor);
    }

    public boolean containsSelectedActor(IWfActor actor) {
        if (isSelectUser()) {
            if (!IWfActor.ACTOR_TYPE_USER.equals(actor.getActorType()))
                return false;
            return containsUser(actor.getActorId());
        }
        return containsActor(actor);
    }

    public int getVoteWeight() {
        return voteWeight;
    }

    public void setVoteWeight(int voteWeight) {
        this.voteWeight = voteWeight;
    }
}