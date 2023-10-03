package io.nop.dev.core.store;

import java.util.Map;

public interface IDevModelStore {
    Map<String, Object> loadModel(String path);

    void saveModel(String path, Map<String, Object> data);
}
