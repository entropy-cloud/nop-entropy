package io.nop.cluster.health;

public interface IHealthChecker {
    HealthCheckResult checkHealth(boolean includeDetails);
}