/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.api.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.beans.ExtensibleBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@DataBean
public class LoginUserInfo extends ExtensibleBean {
    private String tenantId;
    private String userName;
    private String nickName;
    private String locale;
    private String timeZone;
    private String homePath;
    private String deptId;

    private String deptName;
    private List<RoleInfo> roleInfos;
    private String openId;

    private String userId;
    private String avatar;


    @PropMeta(propId = 1)
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @PropMeta(propId = 2)
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @PropMeta(propId = 3)
    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    @PropMeta(propId = 4)
    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    @PropMeta(propId = 5)
    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    @PropMeta(propId = 6)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @PropMeta(propId = 7)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    @PropMeta(propId = 8)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    @PropMeta(propId = 9)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getOpenId() {
        return openId;
    }

    public void setOpenId(String openId) {
        this.openId = openId;
    }

    @PropMeta(propId = 10)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getHomePath() {
        return homePath;
    }

    public void setHomePath(String homePath) {
        this.homePath = homePath;
    }

    @PropMeta(propId = 11)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    @PropMeta(propId = 12)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<RoleInfo> getRoleInfos() {
        return roleInfos;
    }

    public void setRoleInfos(List<RoleInfo> roleInfos) {
        this.roleInfos = roleInfos;
    }

    public void setRoles(Set<String> roleIds) {
        if (roleIds == null) {
            roleIds = Collections.emptySet();
        }
        roleInfos = new ArrayList<>();
        for (String roleId : roleIds) {
            RoleInfo roleInfo = new RoleInfo();
            roleInfo.setRoleId(roleId);
            roleInfos.add(roleInfo);
        }
    }


    @PropMeta(propId = 13)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getAttrs() {
        return super.getAttrs();
    }
}
