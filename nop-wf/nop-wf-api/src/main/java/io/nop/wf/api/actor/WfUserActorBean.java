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
    private String name;

    private String deptId;

    @JsonIgnore
    @Override
    public String getType() {
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
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
