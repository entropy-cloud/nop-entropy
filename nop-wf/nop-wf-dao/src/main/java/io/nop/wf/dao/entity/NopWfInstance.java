/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.store.IWorkflowRecord;
import io.nop.wf.dao.entity._gen._NopWfInstance;

import java.util.Collection;
import java.util.Set;


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
        Integer prevStatus = getStatus();
        setStatus(status);
        if (prevStatus != null && prevStatus != status) {
            NopWfStatusHistory history = new NopWfStatusHistory();
            history.setWfId(getWfId());
            history.setFromStatus(prevStatus);
            history.setToStatus(status);
            history.setToAppState(getAppState());
            history.setChangeTime(getLastOperateTime());
            history.setOperatorId(getLastOperatorId());
            history.setOperatorName(getLastOperatorName());
            history.setOperatorDeptId(getLastOperatorDeptId());
            getStatusHistories().add(history);
        }
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
            setLastOperatorDeptId(caller.getDeptId());
        } else {
            setLastOperatorId(null);
            setLastOperateTime(null);
            setLastOperatorDeptId(null);
        }
    }

    public Set<String> getOnSignals() {
        return ConvertHelper.toCsvSet(getSignalText());
    }

    public void setOnSignals(Set<String> signals) {
        if (signals == null || signals.isEmpty()) {
            setSignalText(null);
        } else {
            setSignalText(StringHelper.join(signals, ","));
        }
    }

    @Override
    public Set<String> getTagSet() {
        return ConvertHelper.toCsvSet(getTagText());
    }

    @Override
    public void addTag(String tag) {
        setTagText(TagsHelper.toString(TagsHelper.add(getTagSet(), tag), ','));
    }

    @Override
    public void addTags(Collection<String> tags) {
        setTagText(TagsHelper.toString(TagsHelper.merge(getTagSet(), tags), ','));
    }

    @Override
    public void removeTag(String tag) {
        setTagText(TagsHelper.toString(TagsHelper.remove(getTagSet(), tag), ','));
    }
}
