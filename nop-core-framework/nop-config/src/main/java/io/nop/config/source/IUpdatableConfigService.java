package io.nop.config.source;

public interface IUpdatableConfigService {
    void publishConfig(String dataId, String group, String content);
}
