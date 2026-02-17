package io.nop.ai.core.model;

import io.nop.ai.core.model._gen._LlmResponseModel;

public class LlmResponseModel extends _LlmResponseModel{
    public LlmResponseModel(){

    }

    /**
     * 获取缓存创建 token 路径
     * <p>
     * 此方法提供与 getPromptCacheMissTokensPath() 的兼容性，
     * 但使用更准确的语义命名。
     */
    public String getPromptCacheCreationTokensPath() {
        return getPromptCacheMissTokensPath();
    }

    /**
     * 设置缓存创建 token 路径
     */
    public void setPromptCacheCreationTokensPath(String value) {
        setPromptCacheMissTokensPath(value);
    }
}
