package io.nop.wf.api.actor;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ExtensibleBean;

@DataBean
public class WfActorAndOwner extends ExtensibleBean {
    private String actorType;
    private String actorId;
    private String actorDeptId;
    private String ownerId;

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

    public String getActorDeptId() {
        return actorDeptId;
    }

    public void setActorDeptId(String actorDeptId) {
        this.actorDeptId = actorDeptId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
}
