package io.nop.lint.core.semantic;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The ServiceLoader discovery of the run's {@link MetricsResolver} (roadmap
 * item 32, plan Decision 5): the first implementation on the classpath
 * serves the CLI's and {@code RuleTestRunner}'s default wiring — the same
 * discovery shape as the language bindings. No implementation means no
 * service (the affected rules degrade; the fail-closed default). The result
 * is cached per process; tests inject resolvers explicitly and never depend
 * on this path.
 */
public final class MetricsResolverDiscovery {

    private static final AtomicReference<MetricsResolver> CACHE = new AtomicReference<>();

    private MetricsResolverDiscovery() {
    }

    /**
     * The first ServiceLoader-provided resolver, or null when the classpath
     * provides none. Cached after the first call.
     */
    public static MetricsResolver discover() {
        MetricsResolver cached = CACHE.get();
        if (cached != null) {
            return cached;
        }
        for (MetricsResolver resolver : ServiceLoader.load(MetricsResolver.class)) {
            CACHE.set(resolver);
            return resolver;
        }
        return null;
    }
}
