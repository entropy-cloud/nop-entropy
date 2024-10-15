/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.api.actor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

@DataBean
public class WfUserActorBean implements IWfActor {
    private String actorId;
    private String actorName;

    private String deptId;

    public String toString() {
        return "WfUserActorBean[actorId=" + actorId + ",actorName=" + actorName + "]";
    }

    @JsonIgnore
    @Override
    public String getActorType() {
        return ACTOR_TYPE_USER;
    }

    @Override
    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Nonnull
    @Override
    public List<? extends IWfActor> getUsers() {
        return Collections.emptyList();
    }

    @Override
    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    @Override
    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }
}
