/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.api.messages;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ExtensibleBean;

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
    private Set<String> roles;
    private String openId;

    private String userId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getOpenId() {
        return openId;
    }

    public void setOpenId(String openId) {
        this.openId = openId;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getHomePath() {
        return homePath;
    }

    public void setHomePath(String homePath) {
        this.homePath = homePath;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    @JsonAnyGetter
    public Map<String, Object> getAttrs() {
        return super.getAttrs();
    }

    @JsonAnySetter
    public void setAttr(String name, Object value) {
        super.setAttr(name, value);
    }
}
