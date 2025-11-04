package io.nop.commons.aggregator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class CompositeAggregatorProvider implements IAggregatorProvider {
    private final Map<String, Supplier<IAggregator>> providerMap = new HashMap<>();


    public static CompositeAggregatorProvider defaultProvider() {
        CompositeAggregatorProvider ret = new CompositeAggregatorProvider();
        ret.addProvider(AGGREGATOR_SUM, SumAggregator::new);
        ret.addProvider(AGGREGATOR_COUNT, CountAggregator::new);
        ret.addProvider(AGGREGATOR_AVG, AverageAggregator::new);
        ret.addProvider(AGGREGATOR_MAX, MaxAggregator::new);
        ret.addProvider(AGGREGATOR_MIN, MinAggregator::new);
        return ret;
    }

    public void addProvider(String name, Supplier<IAggregator> provider) {
        providerMap.put(name, provider);
    }

    @Override
    public IAggregator newAggregator(String name) {
        Supplier<IAggregator> provider = providerMap.get(name);
        if (provider != null)
            return provider.get();
        throw new IllegalArgumentException("unsupported aggregator:" + name);
    }
}