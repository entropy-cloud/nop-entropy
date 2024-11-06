package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;

import java.util.List;
import java.util.stream.Collectors;

public class MultiBatchConsumerProvider<R> implements IBatchConsumerProvider<R> {
    private final List<IBatchConsumerProvider<R>> providers;

    public MultiBatchConsumerProvider(List<IBatchConsumerProvider<R>> providers) {
        this.providers = providers;
    }

    @Override
    public IBatchConsumer<R> setup(IBatchTaskContext context) {
        List<IBatchConsumer<R>> consumers = providers.stream().map(provider -> provider.setup(context)).collect(Collectors.toList());
        return new MultiBatchConsumer<>(consumers);
    }

    public static <R> IBatchConsumerProvider<R> fromList(List<IBatchConsumerProvider<R>> providers) {
        if (providers.isEmpty())
            return null;
        if (providers.size() == 1)
            return providers.get(0);
        return new MultiBatchConsumerProvider<>(providers);
    }
}
