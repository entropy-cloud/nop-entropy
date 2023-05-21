/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.chooser.filter;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiHeaders;
import io.nop.cluster.chooser.IRequestServiceInstanceFilter;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.commons.util.StringHelper;

import java.util.List;

/**
 * 优先选择同一个zone的服务实例
 */
public class ZoneServiceInstanceFilter implements IRequestServiceInstanceFilter<ApiRequest<?>> {
    private String zone;
    private boolean enabled = true;

    /**
     * 当同一个zone内没有匹配的实例时，是否允许选择其他zone的实例
     */
    private boolean force;

    @Override
    public void filter(List<ServiceInstance> instances, ApiRequest<?> request, boolean onlyPreferred) {
        String zone = getRequestZone(request);

        if (StringHelper.isEmpty(zone)) {
            return;
        }

        if (force || !onlyPreferred) {
            instances.removeIf(instance -> !zone.equals(instance.getMetadata(ServiceInstance.META_ZONE)));
        }
    }

    private String getRequestZone(ApiRequest<?> request) {
        String zone = this.zone;
        if (StringHelper.isEmpty(zone))
            zone = ApiHeaders.getAppZone(request);
        return zone;
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