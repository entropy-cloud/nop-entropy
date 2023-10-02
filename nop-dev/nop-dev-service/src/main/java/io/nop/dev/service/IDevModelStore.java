package io.nop.dev.service;

import java.util.Map;

public interface IDevModelStore {
    Map<String, Object> loadModel(String path);

    void saveModel(String path, Map<String, Object> data);
}
