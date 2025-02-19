package io.nop.ai.core.commons.aggregator;

import io.nop.ai.core.api.messages.AiResultMessage;

import java.util.List;

public interface IAiTextAggregator {
    String aggregate(List<AiResultMessage> messages);
}