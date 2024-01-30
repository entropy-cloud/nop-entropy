package io.nop.auth.api.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;

@DataBean
public class RoleInfo {
    private String roleId;
    private String roleName;
    private Boolean primary;

    @PropMeta(propId = 1)
    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    @PropMeta(propId = 2)
    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    @PropMeta(propId = 3)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Boolean getPrimary() {
        return primary;
    }

    public void setPrimary(Boolean primary) {
        this.primary = primary;
    }
}
