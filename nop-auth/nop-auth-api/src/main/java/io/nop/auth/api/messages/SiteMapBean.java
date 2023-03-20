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

    public boolean isSupportDebug() {
        return supportDebug;
    }

    public void setSupportDebug(boolean supportDebug) {
        this.supportDebug = supportDebug;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

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

    public List<SiteResourceBean> getResources() {
        return resources;
    }

    public void setResources(List<SiteResourceBean> resources) {
        this.resources = resources;
    }
}