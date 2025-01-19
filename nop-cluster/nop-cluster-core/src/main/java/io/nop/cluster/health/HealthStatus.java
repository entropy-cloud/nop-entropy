package io.nop.cluster.health;

public enum HealthStatus {
    UNKNOWN,
    UP,
    DOWN,
    OUT_OF_SERVICE;

    public static HealthStatus merge(HealthStatus statusA, HealthStatus statusB) {
        if (statusA.ordinal() <= statusB.ordinal())
            return statusA;
        return statusB;
    }
}