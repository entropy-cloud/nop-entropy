/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.api.actor;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class WfActorBean implements IWfActor{
    private String actorType;
    private String actorId;
    private String deptId;
    private String actorName;

    private List<WfUserActorBean> users;

    public List<WfUserActorBean> getUsers() {
        return users;
    }

    public void setUsers(List<WfUserActorBean> users) {
        this.users = users;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }
}
