package io.nop.ai.coder.orm;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class AiOrmConfig {
    private String basePackageName;

    public String getBasePackageName() {
        return basePackageName;
    }

    public void setBasePackageName(String basePackageName) {
        this.basePackageName = basePackageName;
    }
}
