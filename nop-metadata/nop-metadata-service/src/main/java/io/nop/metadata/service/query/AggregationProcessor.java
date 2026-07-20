package io.nop.metadata.service.query;

import java.util.List;
import java.util.Map;

public interface AggregationProcessor {
    List<Map<String, Object>> execute(AggregationContext context);
}
