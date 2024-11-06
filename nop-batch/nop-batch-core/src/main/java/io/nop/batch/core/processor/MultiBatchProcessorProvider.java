package io.nop.batch.core.processor;

import io.nop.batch.core.IBatchProcessorProvider;
import io.nop.batch.core.IBatchTaskContext;

import java.util.List;
import java.util.stream.Collectors;

public class MultiBatchProcessorProvider<S, R> implements IBatchProcessorProvider<S, R> {
    private final List<IBatchProcessorProvider<?, ?>> providers;

    public MultiBatchProcessorProvider(List<IBatchProcessorProvider<?, ?>> providers) {
        this.providers = providers;
    }

    public static <S, R> IBatchProcessorProvider<S, R> fromList(List<IBatchProcessorProvider<?, ?>> providers) {
        if (providers.isEmpty())
            return null;
        if (providers.size() == 1)
            return (IBatchProcessorProvider<S, R>) providers.get(0);
        return new MultiBatchProcessorProvider<>(providers);
    }

    @Override
    public IBatchProcessor<S, R> setup(IBatchTaskContext taskContext) {
        List<IBatchProcessor<?, ?>> processors = this.providers.stream().map(provider -> provider.setup(taskContext)).collect(Collectors.toList());
        return CompositeBatchProcessor.fromList(processors);
    }
}
