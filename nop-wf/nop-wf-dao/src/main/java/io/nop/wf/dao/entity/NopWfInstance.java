/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.dao.entity._gen._NopWfInstance;


@BizObjName("NopWfInstance")
public class NopWfInstance extends _NopWfInstance implements IWorkflowRecord {
    private boolean willEnd;

    public NopWfInstance() {
    }

    public String getCreaterId() {
        return getCreatedBy();
    }

    @Override
    public void transitToStatus(int status) {
        this.setStatus(status);
    }

    @Override
    public void setStarter(IWfActor starter) {
        if (starter != null) {
            setStarterId(starter.getActorId());
            setStarterName(starter.getActorName());
            setStarterDeptId(starter.getDeptId());
        } else {
            setStarterId(null);
            setStarterName(null);
            setStarterDeptId(null);
        }
    }

    @Override
    public void setManager(IWfActor actor) {
        if (actor != null) {
            setManagerType(actor.getActorType());
            setManagerId(actor.getActorId());
            setManagerName(actor.getActorName());
            setManagerDeptId(actor.getDeptId());
        } else {
            setManagerType(null);
            setManagerId(null);
            setManagerName(null);
            setManagerDeptId(null);
        }
    }

    @Override
    public boolean willEnd() {
        return willEnd;
    }

    @Override
    public void markEnd() {
        willEnd = true;
    }

    @Override
    public void setLastOperator(IWfActor caller) {
        if (caller != null) {
            setLastOperatorId(caller.getActorId());
            setLastOperatorName(caller.getActorName());
        } else {
            setLastOperatorId(null);
            setLastOperateTime(null);
        }
    }
}
