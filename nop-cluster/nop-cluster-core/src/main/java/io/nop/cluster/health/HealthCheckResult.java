package io.nop.cluster.health;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class HealthCheckResult {
    private HealthStatus status;
    private Map<String, Object> details;

    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
