/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.dao.entity._gen._NopWfStepInstance;


@BizObjName("NopWfStepInstance")
public class NopWfStepInstance extends _NopWfStepInstance implements IWorkflowStepRecord {
    public NopWfStepInstance() {
    }

    @Override
    public void transitToStatus(int status) {
        setStatus(status);
    }

    @Override
    public void setJoinValue(String targetStep, String joinValue) {

    }

    @Override
    public void setActor(IWfActor actor) {
        if (actor != null) {
            setActorType(actor.getType());
            setActorId(actor.getActorId());
            setActorName(actor.getName());
            setActorDeptId(actor.getDeptId());
        } else {
            setActorType(null);
            setActorId(null);
            setActorName(null);
            setActorDeptId(null);
        }
    }

    @Override
    public void setOwner(IWfActor owner) {
        if (owner != null) {
            setOwnerId(owner.getActorId());
            setOwnerName(owner.getName());
        } else {
            setOwnerId(null);
            setOwnerName(null);
        }
    }

    @Override
    public void setSubWfRef(WfReference wfRef) {
        if (wfRef != null) {
            setSubWfId(wfRef.getWfId());
            setSubWfVersion(wfRef.getWfVersion());
            setSubWfName(wfRef.getWfName());
        } else {
            setSubWfId(null);
            setSubWfVersion(null);
            setSubWfName(null);
        }
    }

    @Override
    public void setCaller(IWfActor caller) {
        if (caller != null) {
            setCallerId(caller.getActorId());
            setCallerName(caller.getName());
        } else {
            setCallerId(null);
            setCallerName(null);
        }
    }
}
