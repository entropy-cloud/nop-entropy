package io.nop.cluster.chooser.filter;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.cluster.chooser.IRequestServiceInstanceFilter;
import io.nop.cluster.discovery.ServiceInstance;

import java.util.List;

public class SpecificServiceInstanceFilter implements IRequestServiceInstanceFilter<ApiRequest<?>> {
    private boolean enabled;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void filter(List<ServiceInstance> serviceInstances, ApiRequest<?> request, boolean onlyPreferred) {
        String host = request.getStringProperty(ApiConstants.PROP_TARGET_HOST);
        if (host == null)
            return;

        serviceInstances.removeIf(instance -> !host.equals(instance.getHost()));
    }
}
