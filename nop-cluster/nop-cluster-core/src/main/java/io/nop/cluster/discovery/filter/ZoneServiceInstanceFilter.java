/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.discovery.filter;

import io.nop.cluster.discovery.IServiceInstanceFilter;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.commons.util.StringHelper;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 优先选择同一个zone的服务实例
 */
public class ZoneServiceInstanceFilter implements IServiceInstanceFilter {
    private String zone;
    private boolean enabled = true;

    /**
     * 当同一个zone内没有匹配的实例时，是否允许选择其他zone的实例
     */
    private boolean force;

    @Override
    public boolean accept(ServiceInstance instance) {
        if (StringHelper.isEmpty(zone))
            return true;

        return Objects.equals(zone, instance.getMetadata(ServiceInstance.META_ZONE));
    }

    @Override
    public List<ServiceInstance> filter(List<ServiceInstance> instances) {
        List<ServiceInstance> filtered = instances.stream().filter(this::accept).collect(Collectors.toList());

        if (!force && filtered.isEmpty()) {
            return instances;
        }
        return filtered;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}