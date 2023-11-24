/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.api.actor;

import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;

@DataBean
public class WfActorCandidatesBean {
    private WfAssignmentSelection selection;

    private List<WfActorCandidateBean> candidates = new ArrayList<>();

    public String toString() {
        return "WfActorCandidates[selection=" + selection + ",candidates=" + candidates.toString() + "]";
    }

    public boolean hasActor() {
        return !candidates.isEmpty();
    }

    public WfAssignmentSelection getSelection() {
        return selection;
    }

    public void setSelection(WfAssignmentSelection selection) {
        this.selection = selection;
    }

    public List<WfActorCandidateBean> getActorCandidates() {
        return candidates;
    }

    public void addActorCandidate(IWfActor actor, boolean selectUser, int voteWeight, boolean assignForUser) {
        candidates.add(new WfActorCandidateBean(actor, selectUser, voteWeight, assignForUser));
    }

    public boolean containsUser(String userId) {
        for (WfActorCandidateBean candidate : candidates) {
            if (candidate.containsUser(userId))
                return true;
        }
        return false;
    }

    public boolean containsActor(IWfActor actor) {
        for (WfActorCandidateBean candidate : candidates) {
            if (candidate.containsActor(actor))
                return true;
        }
        return false;
    }

    public boolean containsSelectedActor(IWfActor actor) {
        for (WfActorCandidateBean candidate : candidates) {
            if (candidate.containsSelectedActor(actor))
                return true;
        }
        return false;
    }

    public WfActorCandidateBean findCandidate(IWfActor actor) {
        for (WfActorCandidateBean candidate : candidates) {
            if (candidate.containsSelectedActor(actor))
                return candidate;
        }
        return null;
    }
}