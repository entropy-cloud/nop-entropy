/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.api.actor;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@DataBean
public class WfActorBean implements IWfActor {
    private String actorType;
    private String actorId;
    private String deptId;
    private String actorName;

    private List<WfUserActorBean> users;

    private transient Supplier<List<WfUserActorBean>> usersLoader;

    public String toString() {
        return "WfActorBean[actorType=" + actorType + ",actorId=" + actorId + ",deptId=" + deptId + ",actorName=" + actorName + "]";
    }

    public List<WfUserActorBean> getUsers() {
        if (users == null && usersLoader != null) {
            users = usersLoader.get();
        }
        if (users == null)
            users = Collections.emptyList();
        return users;
    }

    public void setUsersLoader(Supplier<List<WfUserActorBean>> usersLoader) {
        this.usersLoader = usersLoader;
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
