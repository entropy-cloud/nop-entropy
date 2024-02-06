package io.nop.graphql.core.engine;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.IGraphQLHook;

import java.util.Arrays;

public class MetricsGraphQLHook implements IGraphQLHook {
    private final MeterRegistry registry;
    private final String prefix;

    private final Timer executeSuccessTimer;

    private final Timer executeFailureTimer;

    private final Timer invokeSuccessTimer;

    private final Timer invokeFailureTimer;

    private final Timer dataFetchSuccessTimer;

    private final Timer dataFetchFailureTimer;

    public MetricsGraphQLHook() {
        this(GlobalMeterRegistry.instance(), "nop");
    }

    public MetricsGraphQLHook(MeterRegistry registry, String prefix) {
        this.registry = registry;
        this.prefix = prefix;

        Tag statusSuccessTag = Tag.of("status", "SUCCESS");
        Tag statusFailureTag = Tag.of("status", "FAILURE");

        executeSuccessTimer = createTimer("graphql.execute", statusSuccessTag);
        executeFailureTimer = createTimer("graphql.execute", statusFailureTag);
        invokeSuccessTimer = createTimer("graphql.invoke", statusSuccessTag);
        invokeFailureTimer = createTimer("graphql.invoke", statusFailureTag);
        dataFetchSuccessTimer = createTimer("graphql.data-fetch", statusSuccessTag);
        dataFetchFailureTimer = createTimer("graphql.data-fetch", statusFailureTag);
    }

    Timer createTimer(String name, Tag... tags) {
        return registry.timer(meterName(name), Arrays.asList(tags));
    }

    String meterName(String name) {
        if (prefix == null)
            return name;
        return prefix + '.' + name;
    }


    @Override
    public Object beginExecute(IGraphQLExecutionContext context) {
        return Timer.start(registry);
    }

    @Override
    public void endExecute(Object meter, Throwable exception, IGraphQLExecutionContext context) {
        ((Timer.Sample) meter).stop(exception == null ? executeSuccessTimer : executeFailureTimer);
    }

    @Override
    public Object beginInvoke(IDataFetchingEnvironment env) {
        return Timer.start(registry);
    }

    @Override
    public void endInvoke(Object meter, Throwable exception, IDataFetchingEnvironment env) {
        ((Timer.Sample) meter).stop(exception == null ? invokeSuccessTimer : invokeFailureTimer);
    }

    @Override
    public Object beginDataFetch(IDataFetchingEnvironment env) {
        return Timer.start(registry);
    }

    @Override
    public void endDataFetch(Object meter, Throwable exception, IDataFetchingEnvironment env) {
        ((Timer.Sample) meter).stop(exception == null ? dataFetchSuccessTimer : dataFetchFailureTimer);
    }
}
