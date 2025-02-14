package io.nop.ai.core.api.aggregator;

import java.util.List;

public interface IAiTextAggregator {
    String aggregate(List<String> texts);
}