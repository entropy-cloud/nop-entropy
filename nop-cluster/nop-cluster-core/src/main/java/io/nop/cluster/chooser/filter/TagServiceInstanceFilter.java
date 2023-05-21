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
import io.nop.commons.util.TagsHelper;

import java.util.List;
import java.util.Set;

public class TagServiceInstanceFilter implements IRequestServiceInstanceFilter<ApiRequest<?>> {
    private boolean enabled = true;

    private Set<String> tags;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    @Override
    public void filter(List<ServiceInstance> serviceInstances, ApiRequest<?> request, boolean onlyPreferred) {
        Set<String> tags = getTags(request);

        if (tags == null || tags.isEmpty())
            return;
        serviceInstances.removeIf(instance -> TagsHelper.containsAll(instance.getTags(), tags));
    }

    private Set<String> getTags(ApiRequest<?> request) {
        Set<String> tags = this.tags;
        if (tags == null || tags.isEmpty())
            tags = ApiHeaders.getSvcTags(request);
        return tags;
    }
}