package io.nop.ai.coder.orm;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.xml.XNode;
import io.nop.orm.model.OrmModelConstants;

@DataBean
public class AiOrmConfig {
    private String basePackageName;

    public String getBasePackageName() {
        return basePackageName;
    }

    public void setBasePackageName(String basePackageName) {
        this.basePackageName = basePackageName;
    }

    public static AiOrmConfig fromOrmNode(XNode node) {
        String basePackageName = node.attrText(OrmModelConstants.EXT_BASE_PACKAGE_NAME);
        if (basePackageName == null)
            basePackageName = "app";

        AiOrmConfig config = new AiOrmConfig();
        config.setBasePackageName(basePackageName);
        return config;
    }
}
