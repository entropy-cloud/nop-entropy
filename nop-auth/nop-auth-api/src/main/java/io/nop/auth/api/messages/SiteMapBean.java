/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.api.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户菜单树
 */
@DataBean
public class SiteMapBean implements IComponentModel, ISourceLocationSetter {
    private SourceLocation location;

    /**
     * 站点id
     */
    private String id;

    private String locale;

    /**
     * 站点名称
     */
    private String displayName;

    /**
     * 配置版本号
     */
    private String configVersion;

    /**
     * 是否启用调试模式
     */
    private boolean supportDebug;

    /**
     * 菜单项
     */
    private List<SiteResourceBean> resources;

    @PropMeta(propId = 1)
    public boolean isSupportDebug() {
        return supportDebug;
    }

    public void setSupportDebug(boolean supportDebug) {
        this.supportDebug = supportDebug;
    }

    @JsonIgnore
    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    @PropMeta(propId = 2)
    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public SiteMapBean deepClone() {
        SiteMapBean ret = new SiteMapBean();
        ret.setLocation(location);
        ret.setDisplayName(displayName);
        ret.setConfigVersion(configVersion);
        ret.setResources(deepCloneResources());
        return ret;
    }

    List<SiteResourceBean> deepCloneResources() {
        if (resources == null)
            return Collections.emptyList();
        return resources.stream().map(SiteResourceBean::deepClone).collect(Collectors.toList());
    }

    @PropMeta(propId = 3)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @PropMeta(propId = 4)
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @PropMeta(propId = 5)
    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public void addResource(SiteResourceBean entry) {
        if (entry == null)
            return;

        if (resources == null)
            resources = new ArrayList<>();
        resources.add(entry);
    }

    public void sortResources() {
        if (resources != null) {
            resources.sort(Comparator.naturalOrder());
            resources.forEach(SiteResourceBean::sortChildren);
        }
    }

    public void removeInactive() {
        if (resources != null) {
            resources.forEach(SiteResourceBean::removeInactive);
            resources.removeIf(res -> !res.isActive());
        }
    }

    public void removeFunctionPoints() {
        if (resources != null) {
            resources.forEach(SiteResourceBean::removeFunctionPoints);
        }
    }

    @PropMeta(propId = 6)
    public List<SiteResourceBean> getResources() {
        return resources;
    }

    public void setResources(List<SiteResourceBean> resources) {
        this.resources = resources;
    }
}