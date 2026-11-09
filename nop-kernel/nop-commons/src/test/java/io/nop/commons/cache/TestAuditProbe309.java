package io.nop.commons.cache;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class TestAuditProbe309 {
    @Test
    public void probe_mixedHitAndMiss() throws Exception {
        CacheConfig config = new CacheConfig();
        config.setAsync(true);
        LocalCache<String, String> cache = LocalCache.newCache("audit309-" + System.nanoTime(),
                config, key -> key + "-loaded");
        try {
            cache.put("hit", "hit-value");
            System.out.println("PROBE_PUT_HIT done");
            CompletionStage<Map<String, String>> stage = cache.getAllAsync(List.of("hit", "miss"));
            try {
                Map<String, String> r = stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
                System.out.println("PROBE_RESULT=RETURNED " + r);
            } catch (java.util.concurrent.ExecutionException e) {
                System.out.println("PROBE_RESULT=THREW " + e.getCause());
            }
            // miss-only
            try {
                Map<String, String> r2 = cache.getAllAsync(List.of("miss-x")).toCompletableFuture().get(5, TimeUnit.SECONDS);
                System.out.println("PROBE_MISSONLY=RETURNED " + r2);
            } catch (java.util.concurrent.ExecutionException e) {
                System.out.println("PROBE_MISSONLY=THREW " + e.getCause());
            }
            System.out.println("PROBE_HIT_AFTER_MISS_LOADED? " + cache.getIfPresent("miss"));
        } finally { cache.destroy(); }
    }
}
