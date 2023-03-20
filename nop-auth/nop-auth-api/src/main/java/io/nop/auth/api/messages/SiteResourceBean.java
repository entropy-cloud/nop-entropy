/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.api.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.auth.api.AuthApiConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@DataBean
public class SiteResourceBean implements ISourceLocationGetter, ISourceLocationSetter, Comparable<SiteResourceBean> {
    private SourceLocation location;
    private String id;
    private String displayName;
    private String icon;
    private String routePath;
    private String url;
    private String description;

    private String component;
    private Map<String, Object> props;

    private Map<String, Object> meta;

    private String target;

    private boolean hidden;
    private boolean keepAlive;

    /**
     * 登录、退出等不需要校验权限的页面
     */
    private boolean noAuth;

    private List<SiteResourceBean> children;

    private Set<String> depends;
    private Set<String> permissions;

    private String resourceType;

    private int status = AuthApiConstants.RESOURCE_STATUS_ACTIVE;

    private int orderNo;

    public SiteResourceBean cloneInstance() {
        SiteResourceBean ret = new SiteResourceBean();
        ret.setLocation(location);
        ret.setId(id);
        ret.setDisplayName(displayName);
        ret.setIcon(icon);
        ret.setRoutePath(routePath);
        ret.setUrl(url);
        ret.setDescription(description);
        ret.setComponent(component);
        ret.setProps(props);
        ret.setMeta(meta);
        ret.setTarget(target);
        ret.setHidden(hidden);
        ret.setKeepAlive(keepAlive);
        ret.setNoAuth(noAuth);
        ret.setChildren(children);
        ret.setDepends(depends);
        ret.setPermissions(permissions);
        ret.setResourceType(resourceType);
        ret.setStatus(status);
        ret.setOrderNo(orderNo);

        return ret;
    }

    public SiteResourceBean deepClone() {
        SiteResourceBean ret = cloneInstance();
        if (children != null) {
            ret.setChildren(children.stream().map(SiteResourceBean::deepClone).collect(Collectors.toList()));
        }
        return ret;
    }

    public void sortChildren() {
        if (children != null) {
            children.sort(Comparator.naturalOrder());
            children.forEach(SiteResourceBean::sortChildren);
        }
    }

    public int compareTo(SiteResourceBean other) {
        int cmp = Integer.compare(this.orderNo, other.orderNo);
        if (cmp != 0)
            return cmp;
        String s1 = getDisplayName();
        if (s1 == null)
            s1 = getId();

        String s2 = other.getDisplayName();
        if (s2 == null)
            s2 = other.getId();
        return s1.compareTo(s2);
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getRoutePath() {
        return routePath;
    }

    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getProps() {
        return props;
    }

    public void setProps(Map<String, Object> props) {
        this.props = props;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isNoAuth() {
        return noAuth;
    }

    public void setNoAuth(boolean noAuth) {
        this.noAuth = noAuth;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<SiteResourceBean> getChildren() {
        return children;
    }

    public void setChildren(List<SiteResourceBean> children) {
        this.children = children;
    }


    public void addChild(SiteResourceBean entry) {
        if (entry == null)
            return;
        if (this.children == null)
            this.children = new ArrayList<>();
        this.children.add(entry);
    }

    public void removeInactive() {
        if (children != null) {
            children.forEach(SiteResourceBean::removeInactive);
            fixStatus();
            children.removeIf(res -> !res.isActive());
        }
    }

    public void removeFunctionPoints() {
        if (children != null) {
            children.removeIf(res -> AuthApiConstants.RESOURCE_TYPE_FUNCTION_POINT.equals(res.getResourceType()));
            children.forEach(SiteResourceBean::removeFunctionPoints);
        }
    }

    @JsonIgnore
    public boolean isActive() {
        return getStatus() == AuthApiConstants.RESOURCE_STATUS_ACTIVE;
    }

    @SuppressWarnings("PMD.CollapsibleIfStatements")
    void fixStatus() {
        if (getStatus() == AuthApiConstants.RESOURCE_STATUS_ACTIVE) {
            if (children != null && !children.stream().allMatch(SiteResourceBean::isActive)) {
                setStatus(AuthApiConstants.RESOURCE_STATUS_DISABLED);
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> getDepends() {
        return depends;
    }

    public void setDepends(Set<String> depends) {
        this.depends = depends;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    public int getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(int orderNo) {
        this.orderNo = orderNo;
    }
}