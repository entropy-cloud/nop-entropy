package io.nop.commons.aggregator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class AggregateState {
    private final Map<String, IAggregator> aggregators = new HashMap<>();

    private IAggregatorProvider aggregatorProvider;

    protected IAggregatorProvider getAggregatorProvider() {
        if (aggregatorProvider == null)
            this.aggregatorProvider = CompositeAggregatorProvider.defaultProvider();
        return aggregatorProvider;
    }

    public void setAggregatorProvider(IAggregatorProvider aggregatorProvider) {
        this.aggregatorProvider = aggregatorProvider;
    }

    public void initAggregator(String name, String type) {
        IAggregator aggregator = getAggregatorProvider().newAggregator(type);
        aggregators.put(name, aggregator);
    }

    public void reset() {
        aggregators.values().forEach(IAggregator::reset);
    }

    public void aggregate(String name, Object value) {
        IAggregator aggregator = aggregators.get(name);
        aggregator.update(value);
    }

    public Object getResult(String name) {
        return aggregators.get(name).getValue();
    }

    public Map<String, Object> getResults() {
        Map<String, Object> ret = new HashMap<>();
        for (Map.Entry<String, IAggregator> entry : aggregators.entrySet()) {
            ret.put(entry.getKey(), entry.getValue().getValue());
        }
        return ret;
    }

    public void forEachResult(BiConsumer<String, Object> consumer) {
        aggregators.forEach((name, aggregator) -> {
            consumer.accept(name, aggregator.getValue());
        });
    }
}
