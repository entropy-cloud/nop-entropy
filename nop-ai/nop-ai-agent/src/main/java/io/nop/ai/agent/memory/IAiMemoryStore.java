package io.nop.ai.agent.memory;

import java.util.List;
import java.util.Map;

public interface IAiMemoryStore {
    List<AiMemoryItem> getAll(Map<String, Object> filters);

    List<AiMemoryItem> getLastN(int n);

    List<AiMemoryItem> search(String query);

    void add(AiMemoryItem item);
}
