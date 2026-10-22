package io.nop.lint.core.semantic;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The ServiceLoader discovery of the run's {@link TypeResolver} (the L2
 * provider, roadmap item 20/37 production wiring): the first implementation
 * on the classpath serves {@code --project-root} runs — the same discovery
 * shape as the sibling deep resolvers. No implementation means no service:
 * a {@code --project-root} run then fails loudly at the option layer, and
 * L2 rules keep degrading (the fail-closed default). The result is cached
 * per process; tests inject resolvers explicitly and never depend on this
 * path.
 */
public final class TypeResolverDiscovery {

    private static final AtomicReference<TypeResolver> CACHE = new AtomicReference<>();

    private TypeResolverDiscovery() {
    }

    /**
     * The first ServiceLoader-provided resolver, or null when the classpath
     * provides none. Cached after the first call.
     */
    public static TypeResolver discover() {
        TypeResolver cached = CACHE.get();
        if (cached != null) {
            return cached;
        }
        for (TypeResolver resolver : ServiceLoader.load(TypeResolver.class)) {
            CACHE.set(resolver);
            return resolver;
        }
        return null;
    }
}
