package io.nop.cluster.health;

public enum HealthStatus {
    UNKNOWN,
    UP,
    DOWN,
    OUT_OF_SERVICE;

    /**
     * worst-wins 聚合：严重度较高者胜出。严重度排序对齐 Spring Boot
     * SimpleStatusAggregator 默认次序：DOWN 最严重，其后 OUT_OF_SERVICE、UP，
     * UNKNOWN 最轻。
     */
    public static HealthStatus merge(HealthStatus statusA, HealthStatus statusB) {
        if (severityRank(statusA) >= severityRank(statusB))
            return statusA;
        return statusB;
    }

    private static int severityRank(HealthStatus status) {
        switch (status) {
            case DOWN:
                return 3;
            case OUT_OF_SERVICE:
                return 2;
            case UP:
                return 1;
            case UNKNOWN:
            default:
                return 0;
        }
    }
}