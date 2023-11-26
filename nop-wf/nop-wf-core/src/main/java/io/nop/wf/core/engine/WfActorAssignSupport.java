/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.engine;

import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.IWfActorResolver;
import io.nop.wf.api.actor.WfActorCandidateBean;
import io.nop.wf.api.actor.WfActorCandidatesBean;
import io.nop.wf.api.actor.WfAssignmentSelection;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.model.WfAssignmentActorModel;
import io.nop.wf.core.model.WfAssignmentModel;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTOR_CANDIDATES;
import static io.nop.wf.core.NopWfCoreErrors.ARG_VALUE;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_ACTOR_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_ACTOR_TYPE;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_ASSIGNMENT_DYNAMIC_RETURN_NOT_WF_ACTOR;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_ASSIGNMENT_OWNER_EXPR_RESULT_NOT_WF_ACTOR;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_SELECTED_ACTOR_COUNT_NOT_ONE;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_SELECTED_ACTOR_NOT_IN_ASSIGNMENT;

public class WfActorAssignSupport {
    private IWfActorResolver wfActorResolver;

    @Inject
    public void setWfActorResolver(IWfActorResolver wfActorResolver) {
        this.wfActorResolver = wfActorResolver;
    }

    public IWfActor resolveActor(String actorType, String actorId, String deptId) {
        return wfActorResolver.resolveActor(actorType, actorId, deptId);
    }

    public IWfActor resolveUser(String userId) {
        return wfActorResolver.resolveUser(userId);
    }

    public IWfActor getManager(IWfActor actor, int upLevel) {
        return wfActorResolver.getManager(actor, upLevel);
    }

    public IWfActor getDeptManager(IWfActor actor, int upLevel) {
        return wfActorResolver.getDeptManager(actor, upLevel);
    }

    protected List<WfActorWithWeight> getAssignmentActors(WfAssignmentModel assignment, WfRuntime wfRt) {
        if (assignment == null || assignment.getActors() == null || assignment.getActors().isEmpty())
            return Collections.emptyList();

        List<WfActorWithWeight> ret = new ArrayList<>(assignment.getActors().size());
        Set<String> actorKeys = new HashSet<>();
        for (WfAssignmentActorModel item : assignment.getActors()) {
            if (item.isDynamic()) {
                // 动态计算的结果可能是actor或者actor的列表
                List<IWfActor> dynamicActors = getDynamicActors(item, wfRt);
                for (IWfActor actor : dynamicActors) {
                    addActor(ret, item.getActorModelId(),
                            item.getVoteWeight(), actorKeys, actor, item.isAssignForUser());
                }
            } else {
                IWfActor actor = resolveActor(item.getActorType(), item.getActorId(), item.getDeptId());
                addActor(ret, item.getActorModelId(), item.getVoteWeight(), actorKeys, actor, item.isAssignForUser());
            }
        }

        wfRt.setCurrentActorAssignments(ret);

        if (!ret.isEmpty()) {
            if (assignment.getSelectExpr() != null) {
                assignment.getSelectExpr().invoke(wfRt);
            }
        }
        return wfRt.getCurrentActorAssignments();
    }

    private List<IWfActor> getDynamicActors(WfAssignmentActorModel actorModel, WfRuntime wfRt) {
        String tagName = StringHelper.removeHead(actorModel.getActorType(), NopWfCoreConstants.WF_ACTOR_NS_PREFIX);
        wfRt.setValue(NopWfCoreConstants.VAR_ACTOR_MODEL, actorModel);
        Object value = XLang.getTagAction(NopWfCoreConstants.WF_ACTOR_LIB_PATH, tagName).invoke(wfRt);
        if (value == null)
            return Collections.emptyList();

        if (value instanceof IWfActor) {
            return Collections.singletonList((IWfActor) value);
        } else if (value instanceof Collection) {
            for (Object o : (Collection<?>) value) {
                if (!(o instanceof IWfActor)) {
                    throw wfRt.newError(ERR_WF_ASSIGNMENT_DYNAMIC_RETURN_NOT_WF_ACTOR)
                            .source(actorModel).param(ARG_WF_ACTOR_TYPE, actorModel.getActorType());
                }
            }
            return CollectionHelper.toList(value);
        } else {
            throw wfRt.newError(ERR_WF_ASSIGNMENT_DYNAMIC_RETURN_NOT_WF_ACTOR)
                    .source(actorModel).param(ARG_WF_ACTOR_TYPE, actorModel.getActorType());
        }
    }

    private void addActor(Collection<WfActorWithWeight> actors, String actorModelId,
                          int voteWeight, Set<String> actorKeys, IWfActor actor, boolean forUser) {
        if (forUser) {
            List<? extends IWfActor> users = actor.getUsers();

            for (IWfActor user : users) {
                if (actorKeys.add(user.getActorKey())) {
                    actors.add(new WfActorWithWeight(user, actorModelId, voteWeight));
                }
            }
        } else {
            if (actorKeys.add(actor.getActorKey()))
                actors.add(new WfActorWithWeight(actor, actorModelId, voteWeight));
        }
    }

    protected List<WfActorWithWeight> getActors(WfAssignmentModel assignment, String targetStep, WfRuntime wfRt) {
        if (assignment == null) {
            return WfActorWithWeight.toAssignment(wfRt.getSelectedActors(targetStep), null, 1);
        }

        // 如果不需要前台选择，则直接返回后台配置的actor
        WfAssignmentSelection selection = assignment.getSelection();
        if (selection == null || selection == WfAssignmentSelection.auto) {
            return getAssignmentActors(assignment, wfRt);
        }

        // 下面的情况都要求使用前台传入的selectedActors,
        // 根据selectInAssignment参数设置决定是否要检查前台传入actor的合法性
        List<IWfActor> selectedActors = wfRt.getSelectedActors(targetStep);

        if (CollectionHelper.isEmpty(selectedActors))
            return Collections.emptyList();


        WfActorCandidatesBean candidates = this.getActorCandidates(assignment, wfRt);

        switch (assignment.getSelection()) {
            case multiple: {
                List<WfActorWithWeight> ret = new ArrayList<>();
                for (IWfActor actor : selectedActors) {
                    WfActorCandidateBean found = candidates.findCandidate(actor);
                    if (found == null && assignment.isMustInAssignment())
                        throw wfRt.newError(ERR_WF_SELECTED_ACTOR_NOT_IN_ASSIGNMENT)
                                .source(assignment)
                                .param(ARG_WF_ACTOR_TYPE, actor.getActorType())
                                .param(ARG_WF_ACTOR_ID, actor.getActorId())
                                .param(ARG_ACTOR_CANDIDATES, candidates);
                    ret.add(new WfActorWithWeight(actor, found == null ? null : found.getActorModelId(),
                            found == null ? 1 : found.getVoteWeight()));
                }
                return ret;
            }
            case single: {
                if (selectedActors.size() > 1)
                    throw wfRt.newError(ERR_WF_SELECTED_ACTOR_COUNT_NOT_ONE).source(assignment);

                IWfActor actor = selectedActors.get(0);
                WfActorCandidateBean found = candidates.findCandidate(actor);
                if (found == null && assignment.isMustInAssignment())
                    throw wfRt.newError(ERR_WF_SELECTED_ACTOR_NOT_IN_ASSIGNMENT)
                            .source(assignment)
                            .param(ARG_WF_ACTOR_TYPE, actor.getActorType())
                            .param(ARG_WF_ACTOR_ID, actor.getActorId())
                            .param(ARG_ACTOR_CANDIDATES, candidates);

                return Collections.singletonList(new WfActorWithWeight(actor, found == null ? null : found.getActorModelId(),
                        found == null ? 1 : found.getVoteWeight()));
            }
            default:
                throw new UnsupportedOperationException("nop.err.wf.unsupported-assignment-selection");
        }
    }

    protected WfActorCandidatesBean getActorCandidates(WfAssignmentModel assignment, WfRuntime wfRt) {
        if (assignment == null || assignment.getActors() == null || assignment.getActors().isEmpty())
            return new WfActorCandidatesBean();

        WfActorCandidatesBean ret = new WfActorCandidatesBean();
        ret.setSelection(assignment.getSelection());

        for (WfAssignmentActorModel item : assignment.getActors()) {
            if (item.isDynamic()) {
                // 动态计算的结果可能是actor或者actor的列表
                List<IWfActor> dynamicActors = getDynamicActors(item, wfRt);
                for (IWfActor actor : dynamicActors) {
                    ret.addActorCandidate(actor, item.isSelectUser(), item.getActorModelId(),
                            item.getVoteWeight(), item.isAssignForUser());
                }
            } else {
                IWfActor actor = resolveActor(item.getActorType(), item.getActorId(), item.getDeptId());
                ret.addActorCandidate(actor, item.isSelectUser(), item.getActorModelId(),
                        item.getVoteWeight(), item.isAssignForUser());
            }
        }
        return ret;
    }

    protected IWfActor getOwner(WfAssignmentModel assignment, IWfActor actor, WfRuntime wfRt) {
        if (assignment == null || assignment.getDefaultOwnerExpr() == null)
            return null;

        if (actor.getActorType().equals(IWfActor.ACTOR_TYPE_USER))
            return actor;

        Object value = assignment.getDefaultOwnerExpr().invoke(wfRt);
        if (value == null)
            return null;

        if (!(value instanceof IWfActor))
            throw wfRt.newError(ERR_WF_ASSIGNMENT_OWNER_EXPR_RESULT_NOT_WF_ACTOR).param(ARG_VALUE, value);

        return (IWfActor) value;
    }
}