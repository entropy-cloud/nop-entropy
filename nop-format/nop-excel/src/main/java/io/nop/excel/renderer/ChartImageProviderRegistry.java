package io.nop.excel.renderer;

import io.nop.api.core.ioc.BeanContainer;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lookup point for the optional {@link IChartImageProvider} (plan 2365 D42).
 *
 * <p>Discovery order: statically registered instance first (unit tests and manual
 * wiring), then the IoC container via {@code tryGetBeanByType} guarded by
 * {@code isInitialized()} (getBeanByType would throw instead of returning null).
 * When both are absent the provider is not on the classpath and the report
 * renderers silently skip chart rendering.</p>
 */
public class ChartImageProviderRegistry {

    private static final AtomicReference<IChartImageProvider> registered = new AtomicReference<>();

    /**
     * Register a provider programmatically (tests / manual assembly). Replaces any
     * previously registered instance.
     */
    public static void register(IChartImageProvider provider) {
        registered.set(provider);
    }

    /**
     * Remove the statically registered provider.
     */
    public static void unregister() {
        registered.set(null);
    }

    /**
     * Resolve the provider, or null when chart rendering is not available. Never
     * throws for a missing provider.
     */
    public static IChartImageProvider getProvider() {
        IChartImageProvider provider = registered.get();
        if (provider != null)
            return provider;
        if (BeanContainer.isInitialized())
            return BeanContainer.instance().tryGetBeanByType(IChartImageProvider.class);
        return null;
    }
}
