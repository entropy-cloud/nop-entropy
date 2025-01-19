package io.nop.cluster.health;

import java.util.HashMap;
import java.util.Map;

public class CompositeHealthChecker implements IHealthChecker {
    public static final String DEFAULT_NAME = "default";

    private final Map<String, IHealthChecker> checkers;

    public CompositeHealthChecker(Map<String, IHealthChecker> checkers) {
        this.checkers = checkers;
    }

    @Override
    public HealthCheckResult checkHealth(boolean includeDetails) {
        Map<String, Object> details = new HashMap<>();
        HealthStatus status = null;

        for (Map.Entry<String, IHealthChecker> entry : checkers.entrySet()) {
            String key = entry.getKey();
            HealthCheckResult result = entry.getValue().checkHealth(includeDetails);

            if (status == null) {
                status = result.getStatus();
            } else {
                status = HealthStatus.merge(status, result.getStatus());
            }

            if (result.getDetails() != null) {
                if (key.equals(DEFAULT_NAME)) {
                    details.putAll(result.getDetails());
                } else {
                    details.put(key, result.getDetails());
                }
            }
        }

        if (status == null)
            status = HealthStatus.UP;

        HealthCheckResult ret = new HealthCheckResult();
        ret.setStatus(status);
        ret.setDetails(details);
        return ret;
    }
}